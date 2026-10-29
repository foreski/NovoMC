package com.novomc.modern;

import com.google.gson.JsonElement;
import com.novomc.client.config.Catalog;
import com.novomc.client.net.NovoPresence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Novo Capes on modern Minecraft (AbstractClientPlayerMixin), and the shared
 * {@link NovoPresence} that says who wears which. Sharing follows the badge's
 * "shareMine" setting from modules.json, like on 1.8.9; turning Novo Capes
 * off stops presence entirely here, since modern has no badge yet.
 */
public final class Cosmetics {
    private Cosmetics() {}

    public static final class NovoCapes extends Module {
        private static volatile boolean on;
        private static volatile boolean showOthers;

        public NovoCapes() {
            super("novo_capes");
        }

        @Override
        protected void onEnable() {
            on = true;
            NovoPresence.INSTANCE.refresh();
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        @Override
        public void tick() {
            showOthers = bool("showOthers");
        }
    }

    private static final Map<String, ClientAsset.Texture> TEXTURES = new HashMap<String, ClientAsset.Texture>();
    private static final Map<UUID, PlayerSkin[]> SKINS = new HashMap<UUID, PlayerSkin[]>();
    /** Animated capes: id -> {frames, frame duration in ms}. Keep in step with relay/src/capes.js. */
    private static final Map<String, int[]> ANIMATED = new HashMap<String, int[]>();

    static {
        ANIMATED.put("aurora_flow", new int[] {24, 83});
        ANIMATED.put("ember_flow", new int[] {24, 83});
    }

    /** The player's skin with their Novo cape, or {@code skin} as it is (render thread). */
    public static PlayerSkin withCape(UUID id, PlayerSkin skin) {
        if (!NovoCapes.on || id == null) {
            return skin;
        }
        String cape = NovoPresence.INSTANCE.cape(id);
        if (cape == null || !(NovoCapes.showOthers || id.equals(Minecraft.getInstance().getUser().getProfileId()))) {
            return skin;
        }
        // Animated capes swap frame files from the wall clock, only while this is drawn.
        int[] animation = ANIMATED.get(cape);
        String file = animation == null ? cape : cape + "_" + ((int) ((System.currentTimeMillis() / animation[1]) % animation[0]));
        ClientAsset.Texture tex = TEXTURES.get(file);
        if (tex == null) {
            Identifier path = Identifier.fromNamespaceAndPath("novomc", "textures/capes/" + file + ".png");
            tex = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("novomc", "capes/" + file), path);
            TEXTURES.put(file, tex);
        }
        // getSkin() runs every frame; reuse the patched record while nothing changes.
        PlayerSkin[] cached = SKINS.get(id);
        if (cached != null && cached[0] == skin && cached[1].cape() == tex) {
            return cached[1];
        }
        // A real elytra texture stays; without one, the elytra uses the cape (ours paints that area).
        PlayerSkin patched = new PlayerSkin(skin.body(), tex, skin.elytra(), skin.model(), skin.secure());
        SKINS.put(id, new PlayerSkin[] {skin, patched});
        if (SKINS.size() > 512) {
            SKINS.clear();
        }
        return patched;
    }

    private static final NovoPresence.Game GAME = new NovoPresence.Game() {
        private final Minecraft mc = Minecraft.getInstance();

        public UUID self() {
            return mc.getUser().getProfileId();
        }

        public String name() {
            return mc.getUser().getName();
        }

        public void join(String serverId) throws Exception {
            mc.services().sessionService().joinServer(mc.getUser().getProfileId(), mc.getUser().getAccessToken(), serverId);
        }

        public Iterable<UUID> players() {
            List<UUID> out = new ArrayList<UUID>();
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                for (PlayerInfo info : connection.getOnlinePlayers()) {
                    out.add(info.getProfile().id());
                }
            }
            return out;
        }
    };

    /** Every client tick, whether or not Novo Capes is on (so turning it off says bye). */
    public static void tickPresence(Minecraft mc) {
        JsonElement share = NovoModern.config().setting(Catalog.get("novo_badge"), "shareMine");
        NovoPresence.INSTANCE.tick(mc.level != null ? GAME : null, NovoCapes.on, share == null || share.getAsBoolean());
    }
}
