package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import net.minecraft.client.Minecraft;

public class FpsModule extends TextHud {
    public FpsModule() {
        super("fps");
    }

    @Override
    protected String[] lines(boolean preview) {
        return new String[] {Minecraft.getDebugFPS() + " FPS"};
    }
}
