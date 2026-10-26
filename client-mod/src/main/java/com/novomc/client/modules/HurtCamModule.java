package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Scales the camera tilt when you take damage (MixinEntityRenderer). */
public class HurtCamModule extends Module {
    private static volatile float factor = 1f;

    public HurtCamModule() {
        super("hurt_cam");
    }

    public static float scale(float degrees) {
        return degrees * factor;
    }

    @Override
    public void onSettingsChanged() {
        factor = number("intensity") / 100f;
    }

    @Override
    protected void onDisable() {
        factor = 1f;
    }
}
