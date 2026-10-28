package com.novomc.client.trainer;

import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

/**
 * One practice drill in the trainer world. {@link #setup} runs on the
 * server thread and builds the arena; the rest runs on the client thread.
 * Arenas sit around {@link #ORIGIN}, high above the flat ground.
 */
public abstract class Drill {
    public static final BlockPos ORIGIN = new BlockPos(0, 100, 0);

    public final String id;
    public final String name;
    public final String description;
    public final ResourceLocation icon;
    /** For timed drills: the fastest time wins. */
    public final boolean lowerIsBetter;

    protected Drill(String id, String name, String description, String icon, boolean lowerIsBetter) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = new ResourceLocation("novomc", "textures/gui/icons/" + icon + ".png");
        this.lowerIsBetter = lowerIsBetter;
    }

    /** Builds the arena and kits the player out (server thread). */
    public abstract void setup(WorldServer world, EntityPlayerMP player);

    /** Called every client tick while the drill runs; returns true when it's over. */
    public abstract boolean tick();

    /** Live numbers for the HUD while running. */
    public abstract List<String> status();

    /** The score recorded as a personal best. */
    public abstract float score();

    /** How a score reads ("23 hits", "4.82 s"). */
    public abstract String format(float score);

    /** Extra result lines under the score. */
    public abstract List<String> details();

    /** Server tick while running (moving targets and such). */
    public void serverTick(WorldServer world) {}

    // ---- Arena helpers (server thread) ----

    /** Empties the arena space around the origin. */
    protected static void clear(WorldServer world) {
        for (int x = -12; x <= 12; x++) {
            for (int y = -6; y <= 8; y++) {
                for (int z = -8; z <= 40; z++) {
                    BlockPos p = ORIGIN.add(x, y, z);
                    if (world.getBlockState(p).getBlock() != Blocks.air) {
                        world.setBlockToAir(p);
                    }
                }
            }
        }
    }

    protected static void floor(WorldServer world, int x0, int z0, int x1, int z1, IBlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                world.setBlockState(ORIGIN.add(x, -1, z), state);
            }
        }
    }

    protected static String seconds(float s) {
        return String.format("%.2f s", s);
    }
}
