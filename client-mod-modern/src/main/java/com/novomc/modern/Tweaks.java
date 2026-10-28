package com.novomc.modern;

import com.mojang.blaze3d.platform.InputConstants;
import com.novomc.modern.mixin.OptionInstanceAccessor;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Toggle Sprint, Zoom and Fullbright on modern Minecraft. */
public final class Tweaks {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("novomc", "novomc"));

    private Tweaks() {}

    /** Uses vanilla's own toggle sprint/sneak options; shows the state like the 1.8.9 mod. */
    public static final class ToggleSprint extends TextHud {
        private Boolean savedSprint;
        private Boolean savedCrouch;

        public ToggleSprint() {
            super("toggle_sprint");
        }

        @Override
        protected void onEnable() {
            savedSprint = mc.options.toggleSprint().get();
            savedCrouch = mc.options.toggleCrouch().get();
        }

        @Override
        public void tick() {
            mc.options.toggleSprint().set(true);
            mc.options.toggleCrouch().set(bool("toggleSneak"));
        }

        @Override
        protected void onDisable() {
            if (savedSprint != null) {
                mc.options.toggleSprint().set(savedSprint);
                mc.options.toggleCrouch().set(savedCrouch);
            }
        }

        @Override
        public boolean visible() {
            return bool("showStatus") && mc.player != null && (mc.player.isSprinting() || mc.player.isShiftKeyDown());
        }

        @Override
        protected String[] lines() {
            boolean sneaking = mc.player != null && mc.player.isShiftKeyDown();
            return new String[] {sneaking ? "[Sneaking (Toggled)]" : "[Sprinting (Toggled)]"};
        }
    }

    /** Hold C to zoom; scrolling while zoomed changes how far. */
    public static final class Zoom extends Module {
        public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.novomc.zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY));
        private static Zoom instance;
        private float extra = 1;
        private float current = 1;

        public Zoom() {
            super("zoom");
            instance = this;
        }

        /** The FOV to use, given vanilla's (called every frame by CameraMixin). */
        public static float apply(float fov) {
            Zoom z = instance;
            if (z == null || !z.isEnabled()) {
                return fov;
            }
            float target = KEY.isDown() ? Math.max(1, z.number("factor")) * z.extra : 1;
            if (!KEY.isDown()) {
                z.extra = 1;
            }
            z.current = z.bool("smooth") ? z.current + (target - z.current) * 0.25f : target;
            return fov / z.current;
        }

        /** Mouse wheel while zoomed; true when the zoom used it. */
        public static boolean scroll(double amount) {
            Zoom z = instance;
            if (z == null || !z.isEnabled() || !KEY.isDown() || !z.bool("scroll")) {
                return false;
            }
            z.extra = Math.max(0.5f, Math.min(4f, z.extra * (amount > 0 ? 1.25f : 0.8f)));
            return true;
        }
    }

    /** Brightness far past vanilla's maximum; the previous value comes back when it's off. */
    public static final class Fullbright extends Module {
        private Double saved;

        public Fullbright() {
            super("fullbright");
        }

        @Override
        protected void onEnable() {
            saved = mc.options.gamma().get();
        }

        @Override
        public void tick() {
            // Past the slider's range, so set the value directly.
            ((OptionInstanceAccessor) (Object) mc.options.gamma()).novomc$setValue(15.0);
        }

        @Override
        protected void onDisable() {
            if (saved != null) {
                ((OptionInstanceAccessor) (Object) mc.options.gamma()).novomc$setValue(saved);
            }
        }
    }
}
