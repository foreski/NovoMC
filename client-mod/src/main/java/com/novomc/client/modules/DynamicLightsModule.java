package com.novomc.client.modules;

import com.novomc.client.module.Module;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

/**
 * Dynamic Lights: torches and other glowing items light up the world around
 * whoever holds them, as do dropped glowing items, burning mobs, blazes and
 * fireballs. Chunks near a light are rebuilt when it moves; while they build,
 * the block light the renderer asks for is raised by {@link #light}.
 */
public class DynamicLightsModule extends Module {
    private static final double RANGE = 64;

    /** {x, y, z, level} per light; replaced whole each tick so chunk-builder threads read a consistent set. */
    private static volatile float[][] lights = new float[0][];

    /** Last block and level per entity id, to know what to rebuild when a light moves or goes out. */
    private final Map<Integer, int[]> last = new HashMap<Integer, int[]>();

    public DynamicLightsModule() {
        super("dynamic_lights");
    }

    /** The dynamic block light (0-15) at a block. */
    public static int light(BlockPos pos) {
        float[][] ls = lights;
        if (ls.length == 0) {
            return 0;
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        double best = 0;
        for (float[] l : ls) {
            double dx = x - l[0], dy = y - l[1], dz = z - l[2];
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < l[3] * l[3]) {
                best = Math.max(best, l[3] - Math.sqrt(d2));
            }
        }
        return (int) Math.min(15, Math.round(best));
    }

    /** Combined (sky << 20 | block << 4) light with the dynamic block light added. */
    public static int combine(int combined, BlockPos pos) {
        int dynamic = light(pos);
        int block = combined >> 4 & 15;
        return dynamic > block ? (combined & ~0xF0) | dynamic << 4 : combined;
    }

    /** How brightly an item glows when held or dropped (0 for most items). */
    public static int itemLight(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
            return Block.getBlockFromItem(item).getLightValue();
        }
        if (item == Items.lava_bucket) {
            return 15;
        }
        if (item == Items.blaze_rod || item == Items.nether_star) {
            return 10;
        }
        if (item == Items.glowstone_dust || item == Items.blaze_powder || item == Items.magma_cream || item == Items.prismarine_crystals) {
            return 8;
        }
        return 0;
    }

    private int entityLight(Entity e) {
        int level = 0;
        if (bool("burning") && (e.isBurning() || e instanceof EntityFireball || e instanceof EntityBlaze || e instanceof EntityMagmaCube)) {
            level = e instanceof EntityMagmaCube ? 8 : e instanceof EntityBlaze ? 10 : 15;
        }
        if (e instanceof EntityLivingBase) {
            level = Math.max(level, itemLight(((EntityLivingBase) e).getHeldItem()));
        } else if (e instanceof EntityItem && bool("items")) {
            level = Math.max(level, itemLight(((EntityItem) e).getEntityItem()));
        }
        return level;
    }

    @Override
    public void onTick() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            clear();
            return;
        }
        List<float[]> now = new ArrayList<float[]>();
        Map<Integer, int[]> seen = new HashMap<Integer, int[]>();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (e.isDead || e.getDistanceSqToEntity(mc.thePlayer) > RANGE * RANGE) {
                continue;
            }
            int level = entityLight(e);
            if (level <= 0) {
                continue;
            }
            double y = e.posY + e.getEyeHeight() * 0.75;
            now.add(new float[] {(float) e.posX, (float) y, (float) e.posZ, level});
            int[] at = {(int) Math.floor(e.posX), (int) Math.floor(y), (int) Math.floor(e.posZ), level};
            seen.put(e.getEntityId(), at);
            int[] before = last.get(e.getEntityId());
            if (before == null || before[0] != at[0] || before[1] != at[1] || before[2] != at[2] || before[3] != at[3]) {
                if (before != null) {
                    rebuild(before);
                }
                rebuild(at);
            }
        }
        for (Iterator<Map.Entry<Integer, int[]>> it = last.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, int[]> gone = it.next();
            if (!seen.containsKey(gone.getKey())) {
                rebuild(gone.getValue());
            }
        }
        last.clear();
        last.putAll(seen);
        lights = now.toArray(new float[0][]);
    }

    /** Rebuilds the chunk sections a light at {x, y, z, level} reaches. */
    private void rebuild(int[] at) {
        int r = at[3];
        mc.renderGlobal.markBlockRangeForRenderUpdate(at[0] - r, at[1] - r, at[2] - r, at[0] + r, at[1] + r, at[2] + r);
    }

    private void clear() {
        if (mc.renderGlobal != null && mc.theWorld != null) {
            for (int[] at : last.values()) {
                rebuild(at);
            }
        }
        last.clear();
        lights = new float[0][];
    }

    @Override
    protected void onDisable() {
        clear();
    }
}
