package com.novomc.client.modules;

import com.novomc.client.NovoMC;
import com.novomc.client.module.Module;
import net.minecraft.util.ResourceLocation;

/**
 * Camera trails using vanilla's "phosphor" post-shader with a per-strength
 * persistence value (assets/novomc/shaders/post/motion_blur_N.json).
 * {@link ShaderController} loads it.
 */
public class MotionBlurModule extends Module {
    private static volatile int strength;

    public MotionBlurModule() {
        super("motion_blur");
    }

    /** The shader to use, or null when off. */
    /** Trail left after one 60 FPS frame (0.545 at strength 1 to 0.95 at 10). */
    public static float persistence() {
        return 0.5f + 0.045f * Math.max(1, strength);
    }

    public static ResourceLocation shader() {
        int s = strength;
        return s > 0 ? new ResourceLocation(NovoMC.MODID, "shaders/post/motion_blur_" + s + ".json") : null;
    }

    @Override
    public void onSettingsChanged() {
        strength = Math.max(1, Math.min(10, integer("strength")));
    }

    @Override
    protected void onDisable() {
        strength = 0;
    }
}
