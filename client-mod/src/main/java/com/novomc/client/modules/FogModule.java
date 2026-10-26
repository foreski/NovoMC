package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Removes render-distance fog and can recolor it. Water and lava fog stay. */
public class FogModule extends Module {
    public FogModule() {
        super("fog");
    }

    private static boolean submerged(Block block) {
        return block.getMaterial() == Material.water || block.getMaterial() == Material.lava;
    }

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (isEnabled() && bool("remove") && !submerged(event.block)) {
            GlStateManager.setFog(GL11.GL_EXP);
            event.density = 0f;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFogColor(EntityViewRenderEvent.FogColors event) {
        if (isEnabled() && bool("customColor") && !submerged(event.block)) {
            int c = color("color");
            event.red = ((c >> 16) & 255) / 255f;
            event.green = ((c >> 8) & 255) / 255f;
            event.blue = (c & 255) / 255f;
        }
    }
}
