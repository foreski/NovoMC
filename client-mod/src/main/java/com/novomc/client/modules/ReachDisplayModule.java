package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Distance from your eyes to where your last hit landed. */
public class ReachDisplayModule extends TextHud {
    private static final long SHOW_MS = 5000;
    private double reach;
    private long hitAt;

    public ReachDisplayModule() {
        super("reach_display");
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || event.entityPlayer != mc.thePlayer) {
            return;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit == event.target && hit.hitVec != null) {
            reach = hit.hitVec.distanceTo(eyes);
        } else {
            // Closest point of the target's hitbox.
            AxisAlignedBB b = event.target.getEntityBoundingBox();
            reach = eyes.distanceTo(new Vec3(
                    Math.max(b.minX, Math.min(b.maxX, eyes.xCoord)),
                    Math.max(b.minY, Math.min(b.maxY, eyes.yCoord)),
                    Math.max(b.minZ, Math.min(b.maxZ, eyes.zCoord))));
        }
        hitAt = System.currentTimeMillis();
    }

    @Override
    public boolean visible() {
        return hitAt > 0 && System.currentTimeMillis() - hitAt < SHOW_MS;
    }

    @Override
    protected String[] lines(boolean preview) {
        return new String[] {String.format("%.2f blocks", hitAt > 0 || !preview ? reach : 3.0)};
    }
}
