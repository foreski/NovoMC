package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Enchantment glint color for items and armor (MixinRenderItem, MixinLayerArmorBase). */
public class GlintModule extends Module {
    /** ARGB color, or 0 for vanilla's. */
    private static volatile int color;

    public GlintModule() {
        super("glint");
    }

    public static int color() {
        return color;
    }

    @Override
    public void onSettingsChanged() {
        color = color("color") | 0xFF000000;
    }

    @Override
    protected void onDisable() {
        color = 0;
    }
}
