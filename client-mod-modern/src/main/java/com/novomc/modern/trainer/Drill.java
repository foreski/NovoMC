package com.novomc.modern.trainer;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * One practice drill in the modern trainer world. The arena is built with
 * commands (the trainer world allows them), high above a flat world around
 * {@code 0 100 0}; everything else runs on the client.
 */
public abstract class Drill {
    /** A target that only takes hits: no AI, lots of health, no knockback. */
    protected static final String TARGET = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:1000f,"
            + "attributes:[{id:\"minecraft:max_health\",base:1000},{id:\"minecraft:knockback_resistance\",base:1}]}";

    public final String id;
    public final String name;
    public final String description;
    public final String icon;
    /** For timed drills: the fastest time wins. */
    public final boolean lowerIsBetter;
    protected final Minecraft mc = Minecraft.getInstance();

    protected Drill(String id, String name, String description, String icon, boolean lowerIsBetter) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.lowerIsBetter = lowerIsBetter;
    }

    /** Commands that build the arena and kit the player out (after the shared reset). */
    public abstract List<String> setup();

    /** True to show a 3-second countdown before {@link #tick} starts. */
    public boolean countdown() {
        return true;
    }

    /** Every client tick while the drill runs; returns true when it's over. */
    public abstract boolean tick();

    /** The local player attacked an entity while the drill runs. */
    public void attacked(Entity target) {}

    /** Live numbers for the HUD while running. */
    public abstract List<String> status();

    /** The score recorded as a personal best. */
    public abstract float score();

    /** How a score reads ("23 crystals", "4 / 5 hits"). */
    public abstract String format(float score);

    /** Extra result lines under the score. */
    public abstract List<String> details();

    protected void command(String command) {
        Trainers.run(List.of(command));
    }
}
