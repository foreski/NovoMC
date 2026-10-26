package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Scales or hides title/subtitle text (MixinGuiIngameForge). */
public class TitlesModule extends Module {
    private static volatile boolean hide;
    private static volatile float scale = 1f;

    public TitlesModule() {
        super("titles");
    }

    public static boolean hide() {
        return hide;
    }

    public static float scale() {
        return scale;
    }

    @Override
    public void onSettingsChanged() {
        hide = bool("hide");
        scale = number("scale") / 100f;
    }

    @Override
    protected void onDisable() {
        hide = false;
        scale = 1f;
    }
}
