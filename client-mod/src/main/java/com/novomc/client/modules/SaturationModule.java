package com.novomc.client.modules;

import com.novomc.client.module.TextHud;

public class SaturationModule extends TextHud {
    public SaturationModule() {
        super("saturation");
    }

    @Override
    protected String[] lines(boolean preview) {
        float sat = mc.thePlayer != null ? mc.thePlayer.getFoodStats().getSaturationLevel() : 5f;
        return new String[] {String.format("Saturation %.1f", sat)};
    }
}
