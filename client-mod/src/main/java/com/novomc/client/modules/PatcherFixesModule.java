package com.novomc.client.modules;

import com.novomc.client.mixin.accessor.MinecraftAccessor;
import com.novomc.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Small fixes for 1.8.9 annoyances: borderless fullscreen on F11, the
 * camera sitting behind your eyes (parallax), the inventory jumping right
 * when you have potion effects, and achievement pop-ups.
 */
public class PatcherFixesModule extends Module {
    private static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";
    private static volatile boolean borderlessOn, parallaxOn, inventoryOn;
    private static boolean borderless;
    private static int windowedW, windowedH;

    public PatcherFixesModule() {
        super("patcher_fixes");
    }

    public static boolean parallax() {
        return parallaxOn;
    }

    public static boolean inventoryFix() {
        return inventoryOn;
    }

    /** True if F11 should toggle borderless fullscreen instead of the vanilla exclusive fullscreen. */
    public static boolean borderlessFullscreen() {
        return borderlessOn || borderless;
    }

    public static boolean isBorderless() {
        return borderless;
    }

    /** Switches between a borderless window covering the screen and the previous window. */
    public static void toggleBorderless() {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            DisplayMode desktop = Display.getDesktopDisplayMode();
            if (!borderless) {
                windowedW = Display.getWidth();
                windowedH = Display.getHeight();
                System.setProperty(UNDECORATED, "true");
                Display.setDisplayMode(new DisplayMode(desktop.getWidth(), desktop.getHeight()));
                Display.setLocation(0, 0);
                Display.setResizable(false);
            } else {
                System.setProperty(UNDECORATED, "false");
                int w = Math.max(320, windowedW);
                int h = Math.max(240, windowedH);
                Display.setDisplayMode(new DisplayMode(w, h));
                Display.setLocation((desktop.getWidth() - w) / 2, (desktop.getHeight() - h) / 2);
                Display.setResizable(true);
            }
            borderless = !borderless;
            ((MinecraftAccessor) mc).novomc$resize(Display.getWidth(), Display.getHeight());
            Display.setVSyncEnabled(mc.gameSettings.enableVsync);
            mc.updateDisplay();
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't switch borderless fullscreen: " + e);
        }
    }

    @Override
    public void onSettingsChanged() {
        borderlessOn = bool("borderless");
        parallaxOn = bool("parallax");
        inventoryOn = bool("inventoryPosition");
        if (!borderlessOn && borderless) {
            toggleBorderless();
        }
    }

    @Override
    protected void onEnable() {
        onSettingsChanged();
    }

    @Override
    protected void onDisable() {
        borderlessOn = parallaxOn = inventoryOn = false;
        if (borderless) {
            toggleBorderless();
        }
    }

    @Override
    public void onTick() {
        if (bool("hideAchievements")) {
            mc.guiAchievement.clearAchievements();
        }
    }
}
