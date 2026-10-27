package com.novomc.client.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.novomc.client.module.Module;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * NovoMC Badge: the NovoMC logo next to the names of players who are on
 * NovoMC, on nametags and in the tab list, like Lunar Client's icon.
 *
 * Once a minute the client tells the relay it's here (proved through Mojang's
 * session server, so nobody can claim someone else's badge) and asks which of
 * the players in the tab list are on NovoMC. Only UUIDs leave the game.
 */
public class NovoBadgeModule extends Module {
    public static final int SIZE = 8;
    private static final ResourceLocation BADGE = new ResourceLocation("novomc", "textures/gui/badge.png");
    private static final String RELAY = System.getProperty("novomc.relay", "").trim();
    private static final int MAX_LOOKUP = 120;
    private static final long HELLO_RETRY_MS = 5 * 60 * 1000;

    private static volatile boolean on;
    private static volatile boolean showOthers;
    private static volatile Set<UUID> online = Collections.emptySet();

    private final ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NovoMC badge");
            t.setDaemon(true);
            return t;
        }
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private final SecureRandom random = new SecureRandom();
    private volatile String session;
    private volatile long helloAfter;
    private Set<UUID> asked = new HashSet<UUID>();
    private int ticks;

    public NovoBadgeModule() {
        super("novo_badge");
    }

    // ---- Who gets a badge (render thread) ----

    public static boolean has(UUID id) {
        if (!on || id == null || !online.contains(id)) {
            return false;
        }
        return showOthers || id.equals(self());
    }

    /** True for a player's own name label (not the score line under it). */
    public static boolean labelHas(Entity entity, String label) {
        return entity instanceof EntityPlayer && has(entity.getUniqueID())
                && EnumChatFormatting.getTextWithoutFormattingCodes(label).contains(entity.getName());
    }

    public static boolean tabHas(NetworkPlayerInfo info) {
        return info != null && has(info.getGameProfile().getId());
    }

    /** Draws the badge with its top-left corner at x, y (current GL transform). */
    public static void draw(float x, float y, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(BADGE);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, alpha);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, SIZE, SIZE, SIZE, SIZE);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static UUID self() {
        return Minecraft.getMinecraft().getSession().getProfile().getId();
    }

    // ---- Syncing (client tick, network on a background thread) ----

    @Override
    public void onSettingsChanged() {
        showOthers = bool("showOthers");
        if (!bool("shareMine") && session != null) {
            final String s = session;
            session = null;
            pool.execute(new Runnable() {
                public void run() {
                    bye(s);
                }
            });
        }
        ticks = 0;
        asked.clear();
    }

    @Override
    protected void onEnable() {
        on = true;
        onSettingsChanged();
    }

    @Override
    protected void onDisable() {
        on = false;
        online = Collections.emptySet();
        if (session != null) {
            final String s = session;
            session = null;
            pool.execute(new Runnable() {
                public void run() {
                    bye(s);
                }
            });
        }
    }

    @Override
    public void onTick() {
        if (RELAY.isEmpty() || mc.theWorld == null) {
            return;
        }
        Set<UUID> here = new LinkedHashSet<UUID>();
        here.add(self());
        NetHandlerPlayClient handler = mc.getNetHandler();
        if (handler != null) {
            for (NetworkPlayerInfo info : handler.getPlayerInfoMap()) {
                UUID id = info.getGameProfile().getId();
                // Version-2 UUIDs are server-side NPCs (Hypixel's), never real players.
                if (id != null && id.version() != 2 && here.size() < MAX_LOOKUP) {
                    here.add(id);
                }
            }
        }
        boolean someoneNew = ticks % 40 == 0 && !asked.containsAll(here);
        if ((ticks++ % 1200 == 0 || someoneNew) && busy.compareAndSet(false, true)) {
            asked = here;
            final Set<UUID> ask = here;
            final boolean share = bool("shareMine");
            pool.execute(new Runnable() {
                public void run() {
                    try {
                        sync(ask, share);
                    } catch (Exception e) {
                        System.err.println("[NovoMC] Badge sync failed: " + e);
                    } finally {
                        busy.set(false);
                    }
                }
            });
        }
    }

    private void sync(Set<UUID> ask, boolean share) throws Exception {
        if (share && session == null && System.currentTimeMillis() >= helloAfter) {
            session = hello();
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
        if (share && session != null && !res.get("session").getAsBoolean()) {
            // The relay restarted or our session lapsed: say hello again next time.
            session = null;
            helloAfter = 0;
            ticks = 1;
        }
    }

    /** Proves who we are through Mojang's session server; returns a relay session or null. */
    private String hello() throws Exception {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        StringBuilder serverId = new StringBuilder();
        for (byte b : bytes) {
            serverId.append(String.format("%02x", b));
        }
        try {
            mc.getSessionService().joinServer(mc.getSession().getProfile(), mc.getSession().getToken(), serverId.toString());
        } catch (Exception e) {
            // The relay will refuse us; the badge simply stays hidden for this account.
            System.err.println("[NovoMC] Badge: session check failed: " + e);
        }
        JsonObject req = new JsonObject();
        req.addProperty("name", mc.getSession().getUsername());
        req.addProperty("serverId", serverId.toString());
        req.addProperty("uuid", self().toString());
        JsonObject res = post("/presence/hello", req);
        return res != null && res.has("session") ? res.get("session").getAsString() : null;
    }

    private void bye(String s) {
        JsonObject req = new JsonObject();
        req.addProperty("session", s);
        try {
            post("/presence/bye", req);
        } catch (Exception ignored) {
            // It expires on its own after a few minutes.
        }
    }

    /** POSTs JSON to the relay; the parsed reply, or null for a non-200 answer. */
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
