package com.novomc.client.trainer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

/**
 * Speedbridge: bridge 30 blocks over the void from the start pad to the gold
 * pad. The clock starts when you step off the start pad; falling resets.
 */
public final class BridgeDrill extends Drill {
    private static final int LENGTH = 30;

    private long startedAt;
    private long finishedAt;
    private boolean fell;

    public BridgeDrill() {
        super("bridge", "Speedbridge", "Bridge 30 blocks over the void to the gold pad. Falling resets.", "layers", true);
    }

    @Override
    public void setup(WorldServer world, EntityPlayerMP player) {
        clear(world);
        floor(world, -1, -1, 1, 1, Blocks.stained_hardened_clay.getStateFromMeta(9));
        floor(world, -1, LENGTH, 1, LENGTH + 2, Blocks.gold_block.getDefaultState());
        player.inventory.clear();
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.sandstone, 64));
        player.inventory.setInventorySlotContents(1, new ItemStack(Blocks.sandstone, 64));
        // Facing back toward the start, the classic backwards-bridging stance.
        player.playerNetServerHandler.setPlayerLocation(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5, 180, 77);
        startedAt = 0;
        finishedAt = 0;
        fell = false;
    }

    @Override
    public boolean tick() {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null) {
            return false;
        }
        double z = p.posZ - ORIGIN.getZ();
        if (startedAt == 0 && z > 1.6) {
            startedAt = System.currentTimeMillis();
        }
        if (p.posY < ORIGIN.getY() - 4) {
            fell = true;
            return true;
        }
        if (startedAt != 0 && z >= LENGTH && p.onGround) {
            finishedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /** Fell off: the run doesn't count (Trainers restarts it). */
    public boolean fell() {
        return fell;
    }

    private float elapsed() {
        long end = finishedAt != 0 ? finishedAt : System.currentTimeMillis();
        return startedAt == 0 ? 0 : (end - startedAt) / 1000f;
    }

    @Override
    public List<String> status() {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        int dist = p == null ? 0 : Math.max(0, Math.min(LENGTH, (int) (p.posZ - ORIGIN.getZ())));
        return Arrays.asList(startedAt == 0 ? "Step off the pad to start" : seconds(elapsed()), dist + " / " + LENGTH + " blocks");
    }

    @Override
    public float score() {
        return elapsed();
    }

    @Override
    public String format(float score) {
        return seconds(score);
    }

    @Override
    public List<String> details() {
        return Collections.singletonList(String.format("%.2f blocks per second", LENGTH / Math.max(0.01f, elapsed())));
    }
}
