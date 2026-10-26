package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;

/**
 * Skips rendering entities and block entities beyond a distance (players
 * optional). Checked in MixinRenderManager and MixinTileEntityRendererDispatcher,
 * before vanilla does any work for them.
 */
public class EntityCullingModule extends Module {
    // Static and cached: these are read for every entity, every frame.
    private static volatile boolean active;
    private static volatile double maxDistanceSq;
    private static volatile double maxTileDistanceSq;
    private static volatile boolean cullPlayers;

    public EntityCullingModule() {
        super("entity_culling");
    }

    @Override
    protected void onEnable() {
        onSettingsChanged();
    }

    @Override
    public void onSettingsChanged() {
        double d = number("distance");
        double t = number("tileDistance");
        maxDistanceSq = d * d;
        maxTileDistanceSq = (t > 0 ? t : 48) * (t > 0 ? t : 48);
        cullPlayers = bool("players");
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
    }

    /** True if `e`, seen from the camera at (x, y, z), is too far to draw. */
    public static boolean cull(Entity e, double x, double y, double z) {
        if (!active || (e instanceof EntityPlayer && !cullPlayers) || e == Minecraft.getMinecraft().getRenderViewEntity()) {
            return false;
        }
        return e.getDistanceSq(x, y, z) > maxDistanceSq;
    }

    /** True if a block entity is too far to draw. Beacon beams stay visible. */
    public static boolean cull(TileEntity te, double x, double y, double z) {
        return active && !(te instanceof TileEntityBeacon) && te.getDistanceSq(x, y, z) > maxTileDistanceSq;
    }
}
