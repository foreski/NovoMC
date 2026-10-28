package com.novomc.modern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import com.novomc.modern.mixin.ChatComponentAccess;
import com.novomc.modern.ui.NovoWaypoints;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The fourth batch of mods on modern, ported from 1.8.9 (same ids, settings and storage). */
public final class More {
    private More() {}

    private static <T extends Module> T on(Class<T> type) {
        for (Module m : NovoModern.modules()) {
            if (type.isInstance(m) && m.isEnabled()) {
                return type.cast(m);
            }
        }
        return null;
    }

    /** World overlays (hitboxes, chunk borders, waypoints), drawn with vanilla's gizmos each frame (DebugRendererMixin). */
    public static void emitGizmos(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Hitbox h = on(Hitbox.class);
        if (h != null) {
            h.emit(partialTick);
        }
        ChunkBorders c = on(ChunkBorders.class);
        if (c != null) {
            c.emit(partialTick);
        }
        Pvp.TntCountdown.emitIfOn(partialTick);
        Waypoints w = on(Waypoints.class);
        if (w != null) {
            w.emit(partialTick);
        }
    }

    /** Sidebar: background opacity, the red numbers and the text shadow (HudMixin). */
    public static final class Scoreboard extends Module {
        public Scoreboard() {
            super("scoreboard");
        }

        public static Scoreboard active() {
            return on(Scoreboard.class);
        }

        public boolean numbers() {
            return bool("numbers");
        }

        public boolean shadow() {
            return bool("shadow");
        }

        /** The title row a little darker than the lines, as vanilla does. */
        public int background(boolean title) {
            float a = number("background") / 100f * (title ? 4 / 3f : 1f);
            return Math.round(Math.min(1f, a) * 255) << 24;
        }
    }

    /** Timestamps on chat lines, and repeated messages stacked into one with a count (ChatComponentMixin). */
    public static final class Chat extends Module {
        private String last;
        private int count;

        public Chat() {
            super("chat");
        }

        public static Component process(ChatComponent chat, Component message) {
            Chat c = on(Chat.class);
            if (c == null) {
                return message;
            }
            String raw = message.getString();
            MutableComponent out = message.copy();
            if (c.bool("stack") && raw.equals(c.last) && !((ChatComponentAccess) chat).novomc$allMessages().isEmpty()) {
                c.count++;
                ((ChatComponentAccess) chat).novomc$allMessages().remove(0);
                ((ChatComponentAccess) chat).novomc$refreshTrimmedMessages();
                out.append(Component.literal(" (x" + c.count + ")").withStyle(ChatFormatting.GRAY));
            } else {
                c.last = raw;
                c.count = 1;
            }
            if (c.bool("timestamps")) {
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern(c.bool("seconds") ? "HH:mm:ss" : "HH:mm"));
                out = Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY).append(out);
            }
            return out;
        }
    }

    /** Your own nametag in third person, and the nametag background (LivingEntityRendererMixin, SubmitNodeCollectionMixin). */
    public static final class Nametags extends Module {
        public Nametags() {
            super("nametags");
        }

        public static boolean showOwn(Entity entity) {
            Nametags n = on(Nametags.class);
            Minecraft mc = Minecraft.getInstance();
            return n != null && n.bool("showOwn") && entity == mc.player && !mc.options.getCameraType().isFirstPerson();
        }

        /** Background opacity (0-1), or -1 for vanilla's. */
        public static float background() {
            Nametags n = on(Nametags.class);
            return n == null ? -1 : n.number("background") / 100f;
        }
    }

    /** Entity hitboxes, optionally only players, with the direction each one looks. */
    public static final class Hitbox extends Module {
        private static final double RANGE = 64;

        public Hitbox() {
            super("hitbox");
        }

        void emit(float pt) {
            int color = color("color");
            boolean playersOnly = bool("playersOnly");
            boolean look = bool("lookVector");
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            for (Entity e : mc.level.entitiesForRendering()) {
                if ((e == mc.player && firstPerson) || (playersOnly && !(e instanceof Player)) || e.distanceToSqr(mc.player) > RANGE * RANGE
                        || e.isInvisible()) {
                    continue;
                }
                Vec3 offset = e.getPosition(pt).subtract(e.position());
                AABB box = e.getBoundingBox().move(offset);
                Gizmos.cuboid(box, GizmoStyle.stroke(color));
                if (look) {
                    Vec3 eye = e.getEyePosition(pt);
                    Gizmos.line(eye, eye.add(e.getViewVector(pt).scale(2)), 0xFF2050FF);
                }
            }
        }
    }

    /** The borders of the chunk you're in, with a grid on its walls and the neighbouring chunks' corners. */
    public static final class ChunkBorders extends Module {
        public ChunkBorders() {
            super("chunk_borders");
        }

        void emit(float pt) {
            int color = color("color");
            int faint = (color & 0xFFFFFF) | 0x60000000;
            int x0 = (Mth.floor(mc.player.getX()) >> 4) << 4;
            int z0 = (Mth.floor(mc.player.getZ()) >> 4) << 4;
            double minY = mc.level.getMinY();
            double maxY = mc.level.getMaxY() + 1;
            // Neighbouring chunks' corners.
            for (int dx = -16; dx <= 32; dx += 16) {
                for (int dz = -16; dz <= 32; dz += 16) {
                    if (dx < 0 || dx > 16 || dz < 0 || dz > 16) {
                        Gizmos.line(new Vec3(x0 + dx, minY, z0 + dz), new Vec3(x0 + dx, maxY, z0 + dz), faint);
                    }
                }
            }
            // This chunk: corners, a vertical line every 2 blocks and a ring every 2 blocks near you.
            for (int i = 0; i <= 16; i += 2) {
                float w = i % 16 == 0 ? 2.5f : 1f;
                int c = i % 16 == 0 ? color : faint;
                Gizmos.line(new Vec3(x0 + i, minY, z0), new Vec3(x0 + i, maxY, z0), c, w);
                Gizmos.line(new Vec3(x0 + i, minY, z0 + 16), new Vec3(x0 + i, maxY, z0 + 16), c, w);
                Gizmos.line(new Vec3(x0, minY, z0 + i), new Vec3(x0, maxY, z0 + i), c, w);
                Gizmos.line(new Vec3(x0 + 16, minY, z0 + i), new Vec3(x0 + 16, maxY, z0 + i), c, w);
            }
            int py = Mth.floor(mc.player.getY());
            for (int y = (py >> 1 << 1) - 16; y <= py + 16; y += 2) {
                int c = y % 16 == 0 ? color : faint;
                Gizmos.line(new Vec3(x0, y, z0), new Vec3(x0 + 16, y, z0), c);
                Gizmos.line(new Vec3(x0, y, z0 + 16), new Vec3(x0 + 16, y, z0 + 16), c);
                Gizmos.line(new Vec3(x0, y, z0), new Vec3(x0, y, z0 + 16), c);
                Gizmos.line(new Vec3(x0 + 16, y, z0), new Vec3(x0 + 16, y, z0 + 16), c);
            }
        }
    }

    /**
     * Waypoints: B marks where you stand, M lists them. Each shows a coloured beam
     * and its name and distance, through walls. Saved per server (or singleplayer
     * world) and dimension in the same config/novomc/waypoints.json as 1.8.9.
     */
    public static final class Waypoints extends Module {
        public static final int[] PALETTE = {0xFF5B8CFF, 0xFFB45BFF, 0xFF22D3EE, 0xFF4ADE80, 0xFFFACC15, 0xFFF87171, 0xFFFB923C, 0xFFFFFFFF};
        public static final KeyMapping ADD_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.novomc.waypoint_add", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, Tweaks.CATEGORY));
        public static final KeyMapping LIST_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.novomc.waypoints", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, Tweaks.CATEGORY));
        /** Labels farther than this are drawn at this distance (scaled up) so they stay in range. */
        private static final double LABEL_RANGE = 24;
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        public static final class Waypoint {
            public String name;
            public int x, y, z, dim;
            public int color;
            public boolean visible = true;
        }

        private Map<String, List<Waypoint>> all;

        public Waypoints() {
            super("waypoints");
        }

        public static Waypoints get() {
            for (Module m : NovoModern.modules()) {
                if (m instanceof Waypoints w) {
                    return w;
                }
            }
            throw new IllegalStateException("Waypoints not registered");
        }

        private File file() {
            return new File(NovoModern.gameDir(), "config/novomc/waypoints.json");
        }

        private Map<String, List<Waypoint>> all() {
            if (all == null) {
                all = new LinkedHashMap<String, List<Waypoint>>();
                try {
                    if (file().exists()) {
                        Map<String, List<Waypoint>> read = GSON.fromJson(Files.readString(file().toPath(), StandardCharsets.UTF_8),
                                new TypeToken<LinkedHashMap<String, List<Waypoint>>>() {}.getType());
                        if (read != null) {
                            all.putAll(read);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[NovoMC] Couldn't read waypoints: " + e);
                }
            }
            return all;
        }

        public void save() {
            try {
                file().getParentFile().mkdirs();
                Files.writeString(file().toPath(), GSON.toJson(all()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("[NovoMC] Couldn't save waypoints: " + e);
            }
        }

        /** Which server or world the player is in (same keys as 1.8.9). */
        public String worldKey() {
            ServerData server = mc.getCurrentServer();
            if (server != null) {
                return "server:" + server.ip.toLowerCase();
            }
            IntegratedServer local = mc.getSingleplayerServer();
            if (local != null) {
                return "world:" + local.getWorldPath(LevelResource.ROOT).normalize().getFileName();
            }
            return "unknown";
        }

        /** 1.8.9's dimension numbers: 0 overworld, -1 nether, 1 end. */
        public int dimension() {
            var key = mc.level.dimension();
            return key == Level.NETHER ? -1 : key == Level.END ? 1 : key == Level.OVERWORLD ? 0 : key.identifier().hashCode();
        }

        /** This server's/world's waypoints (all dimensions); edit, then call save(). */
        public List<Waypoint> here() {
            return all().computeIfAbsent(worldKey(), k -> new ArrayList<Waypoint>());
        }

        public Waypoint addHere() {
            List<Waypoint> list = here();
            Waypoint w = new Waypoint();
            w.name = "Waypoint " + (list.size() + 1);
            w.x = Mth.floor(mc.player.getX());
            w.y = Mth.floor(mc.player.getY());
            w.z = Mth.floor(mc.player.getZ());
            w.dim = dimension();
            w.color = PALETTE[list.size() % PALETTE.length];
            list.add(w);
            save();
            return w;
        }

        @Override
        public void tick() {
            if (mc.player == null) {
                return;
            }
            while (ADD_KEY.consumeClick()) {
                if (mc.gui.screen() == null) {
                    Waypoint w = addHere();
                    mc.player.sendSystemMessage(Component.literal("[Novo] ").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal("Added " + w.name + " at " + w.x + ", " + w.y + ", " + w.z).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" (open the list with " + LIST_KEY.getTranslatedKeyMessage().getString() + ")")
                                    .withStyle(ChatFormatting.GRAY)));
                }
            }
            while (LIST_KEY.consumeClick()) {
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new NovoWaypoints(null));
                }
            }
        }

        void emit(float pt) {
            Vec3 cam = mc.gameRenderer.mainCamera().position();
            int dim = dimension();
            boolean beams = bool("beams");
            for (Waypoint w : here()) {
                if (!w.visible || w.dim != dim) {
                    continue;
                }
                Vec3 at = new Vec3(w.x + 0.5, w.y, w.z + 0.5);
                double dist = at.distanceTo(cam);
                if (beams) {
                    Gizmos.line(new Vec3(at.x, mc.level.getMinY(), at.z), new Vec3(at.x, mc.level.getMaxY() + 1, at.z),
                            (w.color & 0xFFFFFF) | 0xD0000000, 4f).setAlwaysOnTop();
                }
                Vec3 label = at.add(0, 1.2, 0);
                float scale = TextGizmo.Style.DEFAULT_SCALE;
                double ld = label.distanceTo(cam);
                if (ld > LABEL_RANGE) {
                    // Pull far labels in, scaled so they read the same size.
                    label = cam.add(label.subtract(cam).scale(LABEL_RANGE / ld));
                    scale *= (float) (LABEL_RANGE / 2.5);
                } else {
                    scale *= (float) Math.max(3, ld / 2.5);
                }
                String text = bool("distance") ? w.name + " (" + Math.round(dist) + "m)" : w.name;
                Gizmos.billboardText(text, label, TextGizmo.Style.forColorAndCentered(w.color).withScale(scale)).setAlwaysOnTop();
            }
        }
    }
}
