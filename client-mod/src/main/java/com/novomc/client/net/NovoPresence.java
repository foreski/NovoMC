package com.novomc.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Who's on Novo Client, for the badge and Novo capes. Shared by the 1.8.9 and
 * modern clients; each gives it a {@link Game} for the version-specific bits.
 *
 * Once a minute (and when new players show up) the client tells the relay
 * it's here, proved through Mojang's session server so nobody can claim
 * someone else's account, and asks which of the players around are on Novo
 * Client and which Novo capes they wear. Only UUIDs leave the game.
 */
public final class NovoPresence {
    public static final NovoPresence INSTANCE = new NovoPresence();

    private static final String RELAY = System.getProperty("novomc.relay", "").trim();
    private static final int MAX_LOOKUP = 120;
    private static final long HELLO_RETRY_MS = 5 * 60 * 1000;

    /** What the presence needs from the running game. */
    public interface Game {
        UUID self();

        String name();

        /** Mojang session-server join with the player's own token. */
        void join(String serverId) throws Exception;

        /** Players in the tab list (real ones; NPCs filtered out). */
        Iterable<UUID> players();
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NovoMC presence");
            t.setDaemon(true);
            return t;
        }
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private final SecureRandom random = new SecureRandom();
    private volatile Set<UUID> online = Collections.emptySet();
    private volatile Map<UUID, String> capes = Collections.emptyMap();
    private volatile Map<UUID, String> wings = Collections.emptyMap();
    private volatile Map<UUID, String> hats = Collections.emptyMap();
    private volatile String session;
    private volatile long helloAfter;
    private Set<UUID> asked = new HashSet<UUID>();
    private boolean wasActive;
    private boolean wasSharing;
    private int ticks;

    private NovoPresence() {}

    public static boolean configured() {
        return !RELAY.isEmpty();
    }

    public boolean isOnline(UUID id) {
        return id != null && online.contains(id);
    }

    /** The Novo cape id this player wears, or null. */
    public String cape(UUID id) {
        return id == null ? null : capes.get(id);
    }

    /** The Novo wings id this player wears, or null. */
    public String wing(UUID id) {
        return id == null ? null : wings.get(id);
    }

    /** The Novo hat id this player wears, or null. */
    public String hat(UUID id) {
        return id == null ? null : hats.get(id);
    }

    /** Asks again soon (after a settings change). */
    public void refresh() {
        ticks = 0;
        asked.clear();
    }

    /**
     * Call every client tick. {@code active}: something shows badges or capes;
     * {@code share}: tell others we're on Novo Client. {@code game} is null
     * outside a world.
     */
    public void tick(Game game, boolean active, boolean share) {
        share = share && active;
        if (wasSharing && !share) {
            leave();
        }
        if (wasActive && !active) {
            online = Collections.emptySet();
            capes = Collections.emptyMap();
            wings = Collections.emptyMap();
            hats = Collections.emptyMap();
        }
        if (active != wasActive || share != wasSharing) {
            refresh();
        }
        wasActive = active;
        wasSharing = share;
        if (!active || RELAY.isEmpty() || game == null) {
            return;
        }
        Set<UUID> here = new LinkedHashSet<UUID>();
        here.add(game.self());
        for (UUID id : game.players()) {
            // Version-2 UUIDs are server-side NPCs (Hypixel's), never real players.
            if (id != null && id.version() != 2 && here.size() < MAX_LOOKUP) {
                here.add(id);
            }
        }
        boolean someoneNew = ticks % 40 == 0 && !asked.containsAll(here);
        if ((ticks++ % 1200 == 0 || someoneNew) && busy.compareAndSet(false, true)) {
            asked = here;
            final Set<UUID> ask = here;
            final boolean sharing = share;
            final Game g = game;
            pool.execute(new Runnable() {
                public void run() {
                    try {
                        sync(g, ask, sharing);
                    } catch (Exception e) {
                        System.err.println("[NovoMC] Presence sync failed: " + e);
                    } finally {
                        busy.set(false);
                    }
                }
            });
        }
    }

    private void leave() {
        if (session == null) {
            return;
        }
        final String s = session;
        session = null;
        pool.execute(new Runnable() {
            public void run() {
                JsonObject req = new JsonObject();
                req.addProperty("session", s);
                try {
                    post("/presence/bye", req);
                } catch (Exception ignored) {
                    // It expires on its own after a few minutes.
                }
            }
        });
    }

    private void sync(Game game, Set<UUID> ask, boolean share) throws Exception {
        if (share && session == null && System.currentTimeMillis() >= helloAfter) {
            session = hello(game);
            if (session == null) {
                helloAfter = System.currentTimeMillis() + HELLO_RETRY_MS;
            }
        }
        JsonObject req = new JsonObject();
        if (share && session != null) {
            req.addProperty("session", session);
        }
        JsonArray ids = new JsonArray();
        for (UUID id : ask) {
            ids.add(new JsonPrimitive(id.toString()));
        }
        req.add("uuids", ids);
        JsonObject res = post("/presence/sync", req);
        if (res == null) {
            return;
        }
        Set<UUID> found = new HashSet<UUID>();
        for (JsonElement e : res.getAsJsonArray("online")) {
            found.add(UUID.fromString(e.getAsString()));
        }
        online = found;
        capes = wornFrom(res, "capes");
        wings = wornFrom(res, "wings");
        hats = wornFrom(res, "hats");
        if (share && session != null && !res.get("session").getAsBoolean()) {
            // The relay restarted or our session lapsed: say hello again next time.
            session = null;
            helloAfter = 0;
            ticks = 1;
        }
    }

    /** A `{uuid: id}` map from a sync response, keeping only ids we could draw. */
    private static Map<UUID, String> wornFrom(JsonObject res, String key) {
        Map<UUID, String> worn = new HashMap<UUID, String>();
        if (!res.has(key) || !res.get(key).isJsonObject()) {
            return worn;
        }
        for (Map.Entry<String, JsonElement> e : res.getAsJsonObject(key).entrySet()) {
            String id = e.getValue().getAsString();
            // Only ids we ship (lowercase, no paths).
            if (id.matches("[a-z0-9_]{1,32}")) {
                worn.put(UUID.fromString(e.getKey()), id);
            }
        }
        return worn;
    }

    /** Proves who we are through Mojang's session server; returns a relay session or null. */
    private String hello(Game game) throws Exception {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        StringBuilder serverId = new StringBuilder();
        for (byte b : bytes) {
            serverId.append(String.format("%02x", b));
        }
        try {
            game.join(serverId.toString());
        } catch (Exception e) {
            // The relay will refuse us; we simply stay hidden for this account.
            System.err.println("[NovoMC] Presence: session check failed: " + e);
        }
        JsonObject req = new JsonObject();
        req.addProperty("name", game.name());
        req.addProperty("serverId", serverId.toString());
        req.addProperty("uuid", game.self().toString());
        JsonObject res = post("/presence/hello", req);
        return res != null && res.has("session") ? res.get("session").getAsString() : null;
    }

    /** POSTs JSON to the relay; the parsed reply, or null for a non-200 answer. */
    @SuppressWarnings("deprecation")
    private static JsonObject post(String path, JsonObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(RELAY + path).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "NovoMC-client");
        OutputStream out = c.getOutputStream();
        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        out.close();
        int status = c.getResponseCode();
        InputStream in = status == 200 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) {
            byte[] chunk = new byte[4096];
            for (int n; (n = in.read(chunk)) > 0;) {
                buf.write(chunk, 0, n);
            }
            in.close();
        }
        c.disconnect();
        return status == 200 ? new JsonParser().parse(new String(buf.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject() : null;
    }
}
