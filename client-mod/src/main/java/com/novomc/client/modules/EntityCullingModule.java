package com.novomc.client.modules;

import com.novomc.client.module.Module;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
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
    private static volatile double distanceScale = 1.0;
    private static volatile boolean occlusionEnabled;
    private static volatile boolean keepPlayerNametags = true;
    private static volatile int occlusionSamples = 2;
    private static volatile long cacheDurationMs = 120L;
    private static final int MAX_RAY_CHECKS_PER_FRAME = 36;
    private static int rayChecksRemaining = MAX_RAY_CHECKS_PER_FRAME;
    private static World cachedWorld;
    private static final Map<Object, Occlusion> OCCLUSION_CACHE = new WeakHashMap<Object, Occlusion>();

    private static final class Occlusion {
        final long checkedAt;
        final boolean hidden;

        Occlusion(long checkedAt, boolean hidden) {
            this.checkedAt = checkedAt;
            this.hidden = hidden;
        }
    }

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
        String aggression = choice("aggression");
        // Scale the configured cutoffs so the strength setting has a clear,
        // predictable effect while preserving each user's chosen base range.
        distanceScale = "conservative".equals(aggression) ? 1.5
                : "aggressive".equals(aggression) ? 0.75 : 1.0;
        occlusionEnabled = bool("occlusion");
        keepPlayerNametags = bool("keepPlayerNametags");
        occlusionSamples = "aggressive".equals(aggression) ? 1
                : "conservative".equals(aggression) ? 3 : 2;
        cacheDurationMs = "aggressive".equals(aggression) ? 60L
                : "conservative".equals(aggression) ? 220L : 120L;
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
        if (e.getDistanceSq(x, y, z) > maxDistanceSq * distanceScale * distanceScale) {
            return true;
        }
        if (!occlusionEnabled || (e instanceof EntityPlayer && keepPlayerNametags)) {
            return false;
        }
        AxisAlignedBB box = e.getEntityBoundingBox();
        double centerY = (box.minY + box.maxY) * 0.5D;
        return occluded(e, x, y, z, box.minX + (box.maxX - box.minX) * 0.5D, centerY,
                box.minZ + (box.maxZ - box.minZ) * 0.5D,
                box.minX + (box.maxX - box.minX) * 0.5D, box.maxY - 0.05D,
                box.minZ + (box.maxZ - box.minZ) * 0.5D,
                box.minX + (box.maxX - box.minX) * 0.5D, box.minY + 0.05D,
                box.minZ + (box.maxZ - box.minZ) * 0.5D);
    }

    /** True if a block entity is too far to draw. Beacon beams stay visible. */
    public static boolean cull(TileEntity te, double x, double y, double z) {
        if (!active || te instanceof TileEntityBeacon) {
            return false;
        }
        if (te.getDistanceSq(x, y, z) > maxTileDistanceSq * distanceScale * distanceScale) {
            return true;
        }
        return occlusionEnabled && occluded(te, x, y, z, te.getPos().getX() + 0.5D,
                te.getPos().getY() + 0.5D, te.getPos().getZ() + 0.5D,
                te.getPos().getX() + 0.5D, te.getPos().getY() + 0.85D,
                te.getPos().getZ() + 0.5D,
                te.getPos().getX() + 0.5D, te.getPos().getY() + 0.15D,
                te.getPos().getZ() + 0.5D);
    }

    /** Reset the conservative ray budget once per entity-render pass. */
    public static void beginRenderFrame() {
        rayChecksRemaining = MAX_RAY_CHECKS_PER_FRAME;
        World world = Minecraft.getMinecraft().theWorld;
        if (world != cachedWorld) {
            OCCLUSION_CACHE.clear();
            cachedWorld = world;
        }
    }

    private static boolean occluded(Object key, double cameraX, double cameraY, double cameraZ,
                                    double centerX, double centerY, double centerZ,
                                    double upperX, double upperY, double upperZ,
                                    double lowerX, double lowerY, double lowerZ) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Occlusion cached = OCCLUSION_CACHE.get(key);
        if (cached != null && now - cached.checkedAt < cacheDurationMs) {
            return cached.hidden;
        }
        if (rayChecksRemaining <= 0) {
            return false;
        }

        boolean hidden = true;
        for (int i = 0; i < occlusionSamples; i++) {
            if (rayChecksRemaining <= 0) {
                return false;
            }
            rayChecksRemaining--;
            double targetX = i == 0 ? centerX : i == 1 ? upperX : lowerX;
            double targetY = i == 0 ? centerY : i == 1 ? upperY : lowerY;
            double targetZ = i == 0 ? centerZ : i == 1 ? upperZ : lowerZ;
            MovingObjectPosition hit = world.rayTraceBlocks(new Vec3(cameraX, cameraY, cameraZ),
                    new Vec3(targetX, targetY, targetZ), false, true, false);
            if (hit == null || hit.getBlockPos() == null
                    || !world.getBlockState(hit.getBlockPos()).getBlock().isOpaqueCube()) {
                hidden = false;
                break;
            }
        }
        OCCLUSION_CACHE.put(key, new Occlusion(now, hidden));
        return hidden;
    }
}
