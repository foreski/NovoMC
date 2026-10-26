package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Dropped items lie flat and tumble while falling (see ItemRendering). Wins over 2D Items. */
public class ItemPhysicsModule extends Module {
    public ItemPhysicsModule() {
        super("item_physics");
    }

    @Override
    protected void onEnable() {
        ItemRendering.physics = true;
    }

    @Override
    protected void onDisable() {
        ItemRendering.physics = false;
    }
}
