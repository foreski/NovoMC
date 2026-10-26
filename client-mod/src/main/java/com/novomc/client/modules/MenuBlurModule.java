package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Blurs the world behind menus; {@link ShaderController} applies it. */
public class MenuBlurModule extends Module {
    private static volatile boolean on;

    public MenuBlurModule() {
        super("menu_blur");
    }

    public static boolean on() {
        return on;
    }

    @Override
    protected void onEnable() {
        on = true;
    }

    @Override
    protected void onDisable() {
        on = false;
    }
}
