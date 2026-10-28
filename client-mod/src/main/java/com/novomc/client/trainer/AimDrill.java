package com.novomc.client.trainer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

/**
 * Aim & Combo: a target strafes in front of you for 30 seconds. Counts hits
 * that land (vanilla's hit cooldown applies, as in a real fight), how many
 * swings were on target, and the longest combo: hits less than a second and
 * a quarter apart, broken by any swing that misses.
 */
public final class AimDrill extends Drill {
    private static final int LENGTH_TICKS = 30 * 20;
    private static final long COMBO_GAP_MS = 1250;

    private volatile int targetId = -1;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile int combo;
    private volatile int bestCombo;
    private volatile long lastHit;
    private int swings;
    /** A swing this tick that hasn't (yet) hit the target. */
    private boolean swingPending;
    private int onTarget;
    private int ticks;
    private double phase;
    private double speed = 0.09;

    public AimDrill() {
        super("aim", "Aim & Combo", "A strafing target for 30 seconds: land as many hits as you can.", "target", false);
    }

    @Override
    public void setup(WorldServer world, EntityPlayerMP player) {
        clear(world);
        floor(world, -6, -6, 6, 9, Blocks.stained_hardened_clay.getStateFromMeta(9));
        player.inventory.clear();
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.diamond_sword));
        player.playerNetServerHandler.setPlayerLocation(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5, 0, 0);

        EntityZombie target = new EntityZombie(world);
        target.setNoAI(true);
        target.enablePersistence();
        // A helmet keeps it from burning in daylight.
        target.setCurrentItemOrArmor(4, new ItemStack(Items.diamond_helmet));
        target.setCustomNameTag("Target");
        target.setAlwaysRenderNameTag(true);
        target.setPosition(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 3.5);
        world.spawnEntityInWorld(target);
        targetId = target.getEntityId();
    }

    @Override
    public void serverTick(WorldServer world) {
        Entity e = world.getEntityByID(targetId);
        if (!(e instanceof EntityZombie)) {
            return;
        }
        EntityZombie target = (EntityZombie) e;
        // Strafe side to side with changing pace, drifting a little in and out.
        if (world.rand.nextInt(40) == 0) {
            speed = 0.05 + world.rand.nextDouble() * 0.08;
        }
        phase += speed;
        double x = ORIGIN.getX() + 0.5 + Math.sin(phase) * 2.6;
        double z = ORIGIN.getZ() + 3.5 + Math.sin(phase * 0.37) * 0.9;
        target.setPositionAndUpdate(x, ORIGIN.getY(), z);
        target.setHealth(target.getMaxHealth());
    }

    /** A hit landed on the target (server thread). */
    void landed(int entityId) {
        if (entityId != targetId) {
            return;
        }
        long now = System.currentTimeMillis();
        hits.incrementAndGet();
        combo = now - lastHit < COMBO_GAP_MS ? combo + 1 : 1;
        bestCombo = Math.max(bestCombo, combo);
        lastHit = now;
    }

    void swung() {
        swings++;
        swingPending = true;
    }

    void attacked(int entityId) {
        if (entityId == targetId) {
            onTarget++;
            swingPending = false;
        }
    }

    @Override
    public boolean tick() {
        // A swing that didn't connect by the end of the tick missed: the combo is over.
        if (swingPending) {
            swingPending = false;
            combo = 0;
        }
        return ++ticks >= LENGTH_TICKS;
    }

    @Override
    public List<String> status() {
        return Arrays.asList(String.format("%.1f s left", (LENGTH_TICKS - ticks) / 20f),
                hits.get() + " hits  ·  combo " + combo);
    }

    @Override
    public float score() {
        return hits.get();
    }

    @Override
    public String format(float score) {
        return Math.round(score) + " hits";
    }

    @Override
    public List<String> details() {
        int pct = swings == 0 ? 0 : Math.round(100f * onTarget / swings);
        return Arrays.asList("On target: " + pct + "% of " + swings + " swings", "Best combo: " + bestCombo);
    }
}
