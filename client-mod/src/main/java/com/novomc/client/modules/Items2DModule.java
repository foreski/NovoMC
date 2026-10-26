package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Dropped items as flat sprites facing the camera (see ItemRendering). */
public class Items2DModule extends Module {
    public Items2DModule() {
        super("items_2d");
    }

    @Override
    protected void onEnable() {
        ItemRendering.flat = true;
    }

    @Override
    protected void onDisable() {
        ItemRendering.flat = false;
    }
}
