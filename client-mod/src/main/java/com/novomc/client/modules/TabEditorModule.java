package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;

/** Player list tweaks applied by MixinGuiPlayerTabOverlay. */
public class TabEditorModule extends Module {
    private static volatile boolean on;
    private static volatile boolean pingNumbers;
    private static volatile boolean hideHeader;
    private static volatile int backgroundAlpha = 0x80;

    public TabEditorModule() {
        super("tab_editor");
    }

    public static boolean pingNumbers() {
        return on && pingNumbers;
    }

    public static boolean hideHeader() {
        return on && hideHeader;
    }

    public static int background(int vanilla) {
        return on ? backgroundAlpha << 24 : vanilla;
    }

    /** Ping as a colored number, right-aligned where the bars would be. */
    public static void drawPing(int width, int x, int y, NetworkPlayerInfo info) {
        int ping = info.getResponseTime();
        String text = ping < 0 ? "?" : String.valueOf(ping);
        int color = ping < 0 ? 0xFFAAAAAA : ping < 75 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : ping < 300 ? 0xFFFFAA00 : 0xFFFF5555;
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        fr.drawStringWithShadow(text, x + width - fr.getStringWidth(text) - 1, y, color);
    }

    @Override
    public void onSettingsChanged() {
        pingNumbers = bool("pingNumbers");
        hideHeader = bool("hideHeader");
        backgroundAlpha = Math.round(number("background") / 100f * 255);
        on = true;
    }

    @Override
    protected void onDisable() {
        on = false;
    }
}
