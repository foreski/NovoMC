package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Raises gamma far past the vanilla maximum; restores it when turned off. */
public class FullbrightModule extends Module {
    private static final float GAMMA = 100f;
    private float saved = 1f;

    public FullbrightModule() {
        super("fullbright");
    }

    @Override
    protected void onEnable() {
        // A previous session may have saved our gamma into options.txt.
        float current = mc.gameSettings.gammaSetting;
        saved = current > 1f ? 1f : current;
        mc.gameSettings.gammaSetting = GAMMA;
    }

    @Override
    public void onTick() {
        mc.gameSettings.gammaSetting = GAMMA;
    }

    @Override
    protected void onDisable() {
        mc.gameSettings.gammaSetting = saved;
    }
}
