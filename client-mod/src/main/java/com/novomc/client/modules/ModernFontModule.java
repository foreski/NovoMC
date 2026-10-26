package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Poppins instead of Minecraft's pixel font in NovoMC's menus and (optionally) its HUD. */
public class ModernFontModule extends Module {
    private static volatile boolean menus;
    private static volatile boolean hud;

    public ModernFontModule() {
        super("modern_font");
    }

    /** Whether text in this context (HUD or menus) uses the modern font. */
    public static boolean use(boolean inHud) {
        return inHud ? hud : menus;
    }

    @Override
    protected void onEnable() {
        onSettingsChanged();
    }

    @Override
    public void onSettingsChanged() {
        menus = true;
        hud = bool("hud");
    }

    @Override
    protected void onDisable() {
        menus = false;
        hud = false;
    }
}
