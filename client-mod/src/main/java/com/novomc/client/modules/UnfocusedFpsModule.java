package com.novomc.client.modules;

import com.novomc.client.module.Module;
import org.lwjgl.opengl.Display;

/** Lowers the frame-rate cap while the window is in the background. */
public class UnfocusedFpsModule extends Module {
    /** The user's own limit while ours is applied, else -1. */
    private int saved = -1;

    public UnfocusedFpsModule() {
        super("unfocused_fps");
    }

    @Override
    public void onTick() {
        boolean focused = Display.isActive();
        if (!focused && saved < 0) {
            saved = mc.gameSettings.limitFramerate;
            mc.gameSettings.limitFramerate = integer("limit");
        } else if (focused) {
            restore();
        }
    }

    @Override
    public void onSettingsChanged() {
        if (saved >= 0) {
            mc.gameSettings.limitFramerate = integer("limit");
        }
    }

    @Override
    protected void onDisable() {
        restore();
    }

    private void restore() {
        if (saved >= 0) {
            mc.gameSettings.limitFramerate = saved;
            saved = -1;
        }
    }
}
