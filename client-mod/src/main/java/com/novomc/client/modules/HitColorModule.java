package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** The tint entities flash when hurt (applied in MixinRendererLivingEntity). */
public class HitColorModule extends Module {
    private static volatile float[] rgba;

    public HitColorModule() {
        super("hit_color");
    }

    public static boolean active() {
        return rgba != null;
    }

    /** Component 0-3 (r, g, b, a) of the hurt tint. */
    public static float component(int i) {
        float[] c = rgba;
        return c != null ? c[i] : 0f;
    }

    @Override
    public void onSettingsChanged() {
        int c = color("color");
        rgba = new float[] {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, number("opacity") / 100f};
    }

    @Override
    protected void onDisable() {
        rgba = null;
    }
}
