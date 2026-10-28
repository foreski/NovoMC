package com.novomc.modern;

import com.mojang.blaze3d.platform.InputConstants;
import com.novomc.modern.mixin.CooldownInstanceAccessor;
import com.novomc.modern.mixin.ItemCooldownsAccessor;
import com.novomc.modern.ui.NovoUi;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The third batch of mods on modern, ported from 1.8.9 (same ids, settings and look). */
public final class Extras {
    private Extras() {}

    /** How strongly the camera tilts when you're hurt (GameRendererMixin). */
    public static final class HurtCam extends Module {
        private static volatile float factor = 1f;

        public HurtCam() {
            super("hurt_cam");
        }

        public static float scale(float degrees) {
            return degrees * factor;
        }

        @Override
        public void tick() {
            factor = number("intensity") / 100f;
        }

        @Override
        protected void onDisable() {
            factor = 1f;
        }
    }

    /** No FOV change from sprinting or speed effects, optionally keeping the bow zoom (CameraMixin). */
    public static final class FovChanger extends Module {
        private static FovChanger instance;

        public FovChanger() {
            super("fov_changer");
            instance = this;
        }

        public static float modifier(float vanilla) {
            FovChanger f = instance;
            if (f == null || !f.isEnabled() || !f.bool("static") || f.mc.player == null) {
                return vanilla;
            }
            var p = f.mc.player;
            if (f.bool("bowZoom") && p.isUsingItem() && p.getUseItem().is(Items.BOW)) {
                // Same curve as vanilla's bow zoom.
                float t = p.getTicksUsingItem() / 20f;
                t = t > 1f ? 1f : t * t;
                return 1f - t * 0.15f;
            }
            return 1f;
        }
    }

    /** Shows clear skies, rain or a storm whatever the server's weather (LevelMixin; the real weather is kept). */
    public static final class WeatherChanger extends Module {
        private static WeatherChanger instance;

        public WeatherChanger() {
            super("weather_changer");
            instance = this;
        }

        /** The rain (or with thunder, the thunder) level to show, or -1 for the real one. */
        public static float level(boolean thunder) {
            WeatherChanger w = instance;
            if (w == null || !w.isEnabled()) {
                return -1;
            }
            String weather = w.choice("weather");
            return thunder ? ("storm".equals(weather) ? 1f : 0f) : ("clear".equals(weather) ? 0f : 1f);
        }
    }

    /** Caps the framerate while the game window isn't focused. */
    public static final class UnfocusedFps extends Module {
        private int saved = -1;

        public UnfocusedFps() {
            super("unfocused_fps");
        }

        @Override
        public void tick() {
            if (!mc.isWindowActive()) {
                if (saved < 0) {
                    saved = mc.options.framerateLimit().get();
                }
                int limit = Math.round(number("limit"));
                if (mc.options.framerateLimit().get() != limit) {
                    mc.options.framerateLimit().set(limit);
                }
            } else {
                restore();
            }
        }

        @Override
        protected void onDisable() {
            restore();
        }

        private void restore() {
            if (saved >= 0) {
                mc.options.framerateLimit().set(saved);
                saved = -1;
            }
        }
    }

    /** A stopwatch on the HUD: its key starts and stops it; Shift + key resets. */
    public static final class Stopwatch extends TextHud {
        public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.novomc.stopwatch", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), Tweaks.CATEGORY));
        private long startedAt;
        private long elapsed;
        private boolean running;

        public Stopwatch() {
            super("stopwatch");
        }

        @Override
        public void tick() {
            while (KEY.consumeClick()) {
                long now = System.currentTimeMillis();
                if (mc.hasShiftDown()) {
                    running = false;
                    elapsed = 0;
                } else if (running) {
                    elapsed += now - startedAt;
                    running = false;
                } else {
                    startedAt = now;
                    running = true;
                }
            }
        }

        @Override
        protected String[] lines() {
            long ms = elapsed + (running ? System.currentTimeMillis() - startedAt : 0);
            return new String[] {String.format("Stopwatch %d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10)};
        }
    }

    /** The resource pack on top. */
    public static final class PackDisplay extends TextHud {
        public PackDisplay() {
            super("pack_display");
        }

        @Override
        protected String[] lines() {
            String name = "Default";
            for (Pack p : mc.getResourcePackRepository().getSelectedPacks()) {
                if (!p.getId().equals("vanilla") && !p.getId().startsWith("fabric") && !p.getId().equals("novomc")) {
                    name = p.getTitle().getString();
                }
            }
            return new String[] {name};
        }
    }

    /** A pulsing red vignette when your health is low. */
    public static final class LowHealth extends Module {
        private static final int STEPS = 16;
        private static LowHealth instance;

        public LowHealth() {
            super("low_health");
            instance = this;
        }

        public static void render(GuiGraphicsExtractor g) {
            LowHealth m = instance;
            if (m == null || !m.isEnabled() || m.mc.player == null || m.mc.player.isCreative() || m.mc.player.isSpectator()) {
                return;
            }
            float hearts = m.mc.player.getHealth() / 2f;
            if (hearts <= 0 || hearts > m.number("hearts")) {
                return;
            }
            // Stronger the closer you are to dying, and pulsing.
            float danger = 1f - (hearts - 0.5f) / Math.max(1f, m.number("hearts"));
            float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            int alpha = Math.round(Math.min(1f, 0.35f + 0.65f * danger) * pulse * m.number("intensity") / 100f * 255);
            alpha = Math.max(0, Math.min(255, alpha));
            int rgb = m.color("color") & 0xFFFFFF;
            int w = g.guiWidth();
            int h = g.guiHeight();
            int edge = Math.round(Math.min(w, h) * 0.22f);
            g.fillGradient(0, 0, w, edge, alpha << 24 | rgb, rgb);
            g.fillGradient(0, h - edge, w, h, rgb, alpha << 24 | rgb);
            // Sides as strips, fading inward.
            for (int i = 0; i < STEPS; i++) {
                int a = Math.round(alpha * (1f - i / (float) STEPS)) << 24 | rgb;
                int x0 = edge * i / STEPS;
                int x1 = edge * (i + 1) / STEPS;
                g.fill(x0, 0, x1, h, a);
                g.fill(w - x1, 0, w - x0, h, a);
            }
        }
    }

    /** Items on cooldown (pearls, wind charges, shields, ...) with the seconds left. */
    public static final class Cooldowns extends HudModule {
        private static final int ROW = 17;

        public Cooldowns() {
            super("cooldowns");
        }

        private List<Object[]> rows() {
            List<Object[]> out = new ArrayList<Object[]>();
            if (mc.player == null) {
                return out;
            }
            ItemCooldowns cd = mc.player.getCooldowns();
            ItemCooldownsAccessor acc = (ItemCooldownsAccessor) cd;
            Map<Identifier, ?> map = acc.novomc$cooldowns();
            Set<Identifier> seen = new HashSet<Identifier>();
            var inv = mc.player.getInventory();
            List<ItemStack> stacks = new ArrayList<ItemStack>();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                stacks.add(inv.getItem(i));
            }
            stacks.add(mc.player.getItemBySlot(EquipmentSlot.OFFHAND));
            for (ItemStack s : stacks) {
                if (s.isEmpty() || !cd.isOnCooldown(s)) {
                    continue;
                }
                Identifier group = cd.getCooldownGroup(s);
                Object inst = map.get(group);
                if (inst != null && seen.add(group)) {
                    int left = ((CooldownInstanceAccessor) inst).novomc$endTime() - acc.novomc$tickCount();
                    out.add(new Object[] {s, Math.max(0, left) / 20f});
                }
            }
            return out;
        }

        @Override
        public boolean visible() {
            return !rows().isEmpty();
        }

        @Override
        public int width() {
            int w = 16;
            for (Object[] r : rows()) {
                w = Math.max(w, 18 + mc.font.width(label(r)));
            }
            return w;
        }

        @Override
        public int height() {
            return Math.max(1, rows().size()) * ROW - 1;
        }

        private static String label(Object[] r) {
            return String.format("%.1fs", (Float) r[1]);
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            int y = 0;
            for (Object[] r : rows()) {
                g.item((ItemStack) r[0], 0, y);
                g.text(mc.font, label(r), 18, y + 4, color("textColor"), true);
                y += ROW;
            }
        }
    }

    /** The enabled mods as a list (the classic "ArrayList"), with a Novo Client watermark. */
    public static final class ModList extends HudModule {
        private static final int LINE = 10;
        private static final int PAD = 3;
        private static final String WATERMARK = "Novo Client";
        private static final Set<String> HIDDEN = new HashSet<String>(Arrays.asList(
                "module_list", "main_menu", "custom_menus", "loading_screen", "unfocused_fps"));

        public ModList() {
            super("module_list");
        }

        private List<String> names() {
            List<String> out = new ArrayList<String>();
            for (Module m : NovoModern.modules()) {
                if (m.isEnabled() && !HIDDEN.contains(m.id()) && !(bool("hideHud") && m instanceof HudModule)) {
                    out.add(m.def.name);
                }
            }
            if ("length".equals(choice("sort"))) {
                out.sort((a, b) -> mc.font.width(b) - mc.font.width(a));
            } else {
                out.sort(String.CASE_INSENSITIVE_ORDER);
            }
            return out;
        }

        @Override
        public boolean visible() {
            return bool("watermark") || !names().isEmpty();
        }

        @Override
        public int width() {
            int w = bool("watermark") ? mc.font.width(WATERMARK) : 0;
            for (String n : names()) {
                w = Math.max(w, mc.font.width(n));
            }
            return w + PAD * 2 + 2;
        }

        @Override
        public int height() {
            return Math.max(1, names().size() + (bool("watermark") ? 1 : 0)) * LINE;
        }

        private int color(int i) {
            String mode = choice("colors");
            if ("white".equals(mode)) {
                return 0xFFFFFFFF;
            }
            float wave = (System.currentTimeMillis() % 4000L) / 4000f - i * 0.06f;
            wave -= (float) Math.floor(wave);
            if ("rainbow".equals(mode)) {
                return Color.HSBtoRGB(wave, 0.75f, 1f) | 0xFF000000;
            }
            // Novo gradient: blue -> purple -> cyan and back.
            float t = (float) (0.5 - 0.5 * Math.cos(wave * Math.PI * 2));
            return t < 0.5f ? NovoUi.blend(0xFF5B8CFF, 0xFFB45BFF, t * 2) : NovoUi.blend(0xFFB45BFF, 0xFF22D3EE, (t - 0.5f) * 2);
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            List<String> lines = new ArrayList<String>(names());
            if (bool("watermark")) {
                lines.add(0, WATERMARK);
            }
            int boxW = width();
            // On the right half of the screen, lines hug the right edge like a classic ArrayList.
            boolean right = x() > 0.5f;
            boolean bar = bool("bar");
            for (int i = 0; i < lines.size(); i++) {
                String s = lines.get(i);
                int w = mc.font.width(s) + PAD * 2;
                int left = right ? boxW - w - 2 : 0;
                int top = i * LINE;
                int c = color(i);
                if (bool("background")) {
                    g.fill(left, top, left + w + 2, top + LINE, 0x80000000);
                }
                if (bar) {
                    int bx = right ? boxW - 2 : left;
                    g.fill(bx, top, bx + 2, top + LINE, c);
                }
                int tx = right ? left + PAD : left + PAD + (bar ? 2 : 0);
                g.text(mc.font, s, tx, top + 1, c, true);
            }
        }
    }
}
