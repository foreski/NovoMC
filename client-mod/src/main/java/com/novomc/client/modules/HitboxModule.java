package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.util.Draw3D;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Entity hitboxes (like F3+B) in a chosen color, optionally with look direction. */
public class HitboxModule extends Module {
    private static final double MAX_DISTANCE_SQ = 64 * 64;

    public HitboxModule() {
        super("hitbox");
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Entity view = mc.getRenderViewEntity();
        if (!isEnabled() || mc.theWorld == null || view == null) {
            return;
        }
        float pt = event.partialTicks;
        double[] cam = Draw3D.camera(view, pt);
        int c = color("color");
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        boolean playersOnly = bool("playersOnly");
        boolean look = bool("lookVector");

        Draw3D.begin(2f);
        for (Entity e : mc.theWorld.loadedEntityList) {
            if ((e == view && mc.gameSettings.thirdPersonView == 0) || (playersOnly && !(e instanceof EntityPlayer))
                    || e.getDistanceSqToEntity(view) > MAX_DISTANCE_SQ) {
                continue;
            }
            double ex = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - cam[0];
            double ey = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - cam[1];
            double ez = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - cam[2];
            AxisAlignedBB box = e.getEntityBoundingBox().offset(ex - e.posX, ey - e.posY, ez - e.posZ);
            RenderGlobal.drawOutlinedBoundingBox(box, r, g, b, 255);
            if (look && e instanceof EntityLivingBase) {
                Vec3 dir = e.getLook(pt);
                double eye = ey + e.getEyeHeight();
                WorldRenderer w = Draw3D.lines();
                Draw3D.line(w, ex, eye, ez, ex + dir.xCoord * 2, eye + dir.yCoord * 2, ez + dir.zCoord * 2, 0xFF5B8CFF);
                Draw3D.drawLines();
            }
        }
        Draw3D.end();
    }
}
