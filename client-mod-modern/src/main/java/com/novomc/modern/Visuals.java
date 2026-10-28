package com.novomc.modern;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;

/** The visual mods on modern, ported from 1.8.9: Crosshair, Hit Color, Block Outline and Freelook. */
public final class Visuals {
    private Visuals() {}

    /** Replaces the vanilla crosshair with a configurable one. */
    public static final class Crosshair extends Module {
        private static Crosshair instance;

        public Crosshair() {
            super("crosshair");
            instance = this;
            HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
                if (instance.isEnabled()) {
                    if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                        instance.draw(g);
                    }
                } else {
                    vanilla.extractRenderState(g, delta);
                }
            });
        }

        private void draw(GuiGraphicsExtractor g) {
            int cx = g.guiWidth() / 2;
            int cy = g.guiHeight() / 2;
            int size = Math.round(number("size"));
            int gap = Math.round(number("gap"));
            int t = Math.max(1, Math.round(number("thickness")));
            String style = choice("style");
            int color = color("color");
            boolean outline = bool("outline");
            int lo = -(t - 1) / 2;
            int hi = lo + t;
            if (!"dot".equals(style)) {
                bar(g, cx - gap - size, cy + lo, cx - gap, cy + hi, color, outline);
                bar(g, cx + 1 + gap, cy + lo, cx + 1 + gap + size, cy + hi, color, outline);
                bar(g, cx + lo, cy + 1 + gap, cx + hi, cy + 1 + gap + size, color, outline);
                if (!"t".equals(style)) {
                    bar(g, cx + lo, cy - gap - size, cx + hi, cy - gap, color, outline);
                }
            }
            if ("dot".equals(style) || bool("centerDot")) {
                int extra = "dot".equals(style) ? 1 : 0;
                bar(g, cx + lo, cy + lo, cx + hi + extra, cy + hi + extra, color, outline);
            }
        }

        private static void bar(GuiGraphicsExtractor g, int l, int t, int r, int b, int color, boolean outline) {
            if (outline) {
                g.fill(l - 1, t - 1, r + 1, b + 1, 0xC0000000);
            }
            g.fill(l, t, r, b, color);
        }
    }

    /** The tint entities flash when hurt: rewrites the overlay texture's red half. */
    public static final class HitColor extends Module {
        /** Vanilla's hurt tint (ARGB; alpha is how much of the entity shows through). */
        private static final int VANILLA = 0xB3FF0000;
        private int applied = VANILLA;

        public HitColor() {
            super("hit_color");
        }

        @Override
        public void tick() {
            int opacity = Math.round(Math.max(0, Math.min(100, number("opacity"))) * 2.55f);
            set((255 - opacity) << 24 | (color("color") & 0xFFFFFF));
        }

        @Override
        protected void onDisable() {
            set(VANILLA);
        }

        private void set(int argb) {
            if (argb != applied && mc.gameRenderer != null) {
                applied = argb;
                ((OverlayTextureAccess) mc.gameRenderer.overlayTexture()).novomc$setHurtColor(argb);
            }
        }
    }

    /** Colour, width and an optional fill for the outline of the block you look at (LevelRendererMixin). */
    public static final class BlockOutline extends Module {
        private static BlockOutline instance;

        public BlockOutline() {
            super("block_outline");
            instance = this;
        }

        public static BlockOutline active() {
            return instance != null && instance.isEnabled() ? instance : null;
        }

        public int lineColor() {
            return color("color");
        }

        public float width() {
            return Math.max(1, number("width"));
        }

        /** ARGB fill, or 0 for none. */
        public int fillColor() {
            if (!bool("fill")) {
                return 0;
            }
            int a = Math.round(Math.max(0, Math.min(100, number("fillOpacity"))) * 2.55f);
            return a << 24 | (color("color") & 0xFFFFFF);
        }
    }

    /**
     * Hold the freelook key (Left Alt) to turn the camera without turning the
     * player. MouseHandlerMixin sends mouse movement here; CameraMixin points
     * the camera at these angles.
     */
    public static final class Freelook extends Module {
        public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.novomc.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, Tweaks.CATEGORY));
        private static volatile boolean active;
        private static float yaw;
        private static float pitch;
        private CameraType saved;

        public Freelook() {
            super("freelook");
        }

        @Override
        public void tick() {
            boolean want = KEY.isDown() && mc.gui.screen() == null && mc.player != null;
            if (want && !active) {
                yaw = mc.player.getYRot();
                pitch = mc.player.getXRot();
                if (bool("thirdPerson") && mc.options.getCameraType().isFirstPerson()) {
                    saved = mc.options.getCameraType();
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                active = true;
            } else if (!want && active) {
                stop();
            }
        }

        private void stop() {
            active = false;
            if (saved != null) {
                mc.options.setCameraType(saved);
                saved = null;
            }
        }

        @Override
        protected void onDisable() {
            if (active) {
                stop();
            }
        }

        /** Takes a mouse movement for the camera; false when freelook is off. */
        public static boolean turn(double dx, double dy) {
            if (!active) {
                return false;
            }
            // Same scaling as Entity.turn.
            yaw += (float) dx * 0.15f;
            pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * 0.15f));
            return true;
        }

        public static float yaw(Entity entity, float vanilla) {
            return active && entity == Minecraft.getInstance().getCameraEntity() ? yaw : vanilla;
        }

        public static float pitch(Entity entity, float vanilla) {
            return active && entity == Minecraft.getInstance().getCameraEntity() ? pitch : vanilla;
        }
    }
}
