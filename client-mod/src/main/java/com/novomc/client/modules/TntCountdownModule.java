package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.util.Draw;
import com.novomc.client.util.Draw3D;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Seconds left floating above lit TNT, green to red. */
public class TntCountdownModule extends Module {
    private static final double MAX_DISTANCE_SQ = 48 * 48;

    public TntCountdownModule() {
        super("tnt_countdown");
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Entity view = mc.getRenderViewEntity();
        if (!isEnabled() || mc.theWorld == null || view == null) {
            return;
        }
        float pt = event.partialTicks;
        double[] cam = Draw3D.camera(view, pt);
        RenderManager rm = mc.getRenderManager();
        FontRenderer fr = mc.fontRendererObj;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityTNTPrimed) || e.getDistanceSqToEntity(view) > MAX_DISTANCE_SQ) {
                continue;
            }
            int fuse = ((EntityTNTPrimed) e).fuse;
            String text = String.format("%.1f", Math.max(0, (fuse - pt) / 20f));
            int color = Draw.blend(0xFFFF5555, 0xFF55FF55, Math.min(1f, fuse / 80f));
            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - cam[0];
            double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - cam[1] + e.height + 0.5;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - cam[2];

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
            GlStateManager.rotate(mc.gameSettings.thirdPersonView == 2 ? -rm.playerViewX : rm.playerViewX, 1, 0, 0);
            GlStateManager.scale(-0.03f, -0.03f, 0.03f);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            int half = fr.getStringWidth(text) / 2;
            Draw.rect(-half - 2, -2, half + 2, 9, 0x50000000);
            fr.drawString(text, -half, 0, color);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
    }
}
