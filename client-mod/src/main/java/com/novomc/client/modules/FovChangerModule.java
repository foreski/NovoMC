package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Keeps the field of view steady while sprinting or under speed effects. */
public class FovChangerModule extends Module {
    public FovChangerModule() {
        super("fov_changer");
    }

    @SubscribeEvent
    public void onFov(FOVUpdateEvent event) {
        if (!isEnabled() || !bool("static")) {
            return;
        }
        EntityPlayer p = event.entity;
        if (bool("bowZoom") && p.isUsingItem() && p.getItemInUse() != null && p.getItemInUse().getItem() == Items.bow) {
            // Same curve as vanilla's bow zoom.
            float f = p.getItemInUseDuration() / 20f;
            f = f > 1f ? 1f : f * f;
            event.newfov = 1f - f * 0.15f;
            return;
        }
        event.newfov = 1f;
    }
}
