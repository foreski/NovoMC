package com.novomc.modern.trainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;

/** The modern drills: crystal speed, mace dives and spear charges. */
public final class Drills {
    private Drills() {}

    public static List<Drill> all() {
        List<Drill> list = new ArrayList<Drill>();
        list.add(new Crystal());
        list.add(new MaceDive());
        list.add(new SpearCharge());
        return list;
    }

    public static Drill create(String id) {
        for (Drill d : all()) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        throw new IllegalArgumentException("No drill " + id);
    }

    /** Place and pop as many end crystals as you can in 20 seconds. */
    static final class Crystal extends Drill {
        private static final int TICKS = 20 * 20;
        private int ticks;
        private int popped;

        Crystal() {
            super("crystal_speed", "Crystal Speed", "Place and pop end crystals on obsidian for 20 seconds", "sparkles", false);
        }

        @Override
        public List<String> setup() {
            return List.of("fill -4 99 -4 4 99 4 minecraft:obsidian", "tp @s 0 100 -3 0 30",
                    "give @s minecraft:end_crystal 64", "give @s minecraft:obsidian 16");
        }

        @Override
        public boolean tick() {
            return ++ticks >= TICKS;
        }

        @Override
        public void attacked(Entity target) {
            if (target instanceof EndCrystal) {
                popped++;
            }
        }

        @Override
        public List<String> status() {
            return List.of(popped + " crystals", String.format("%.1f s left", (TICKS - ticks) / 20f));
        }

        @Override
        public float score() {
            return popped;
        }

        @Override
        public String format(float score) {
            return Math.round(score) + " crystals";
        }

        @Override
        public List<String> details() {
            return List.of(String.format("%.2f crystals a second", popped / (TICKS / 20f)));
        }
    }

    /** Five dives from 25 blocks up: steer onto the target and land a mace smash. */
    static final class MaceDive extends Drill {
        private static final int DIVES = 5;
        private static final int HEIGHT = 125;
        private final Random random = new Random();
        private int dive;
        private int hits;
        private double fallTotal;
        private boolean airborne;
        private boolean hitThisDive;
        private int wait;
        private int diveTicks;

        MaceDive() {
            super("mace_dive", "Mace Dive", "Five drops from 25 blocks: steer onto the target and smash it", "target", false);
        }

        @Override
        public List<String> setup() {
            return List.of("fill -6 99 -6 6 99 6 minecraft:smooth_stone", "summon minecraft:husk 0 100 0 " + TARGET,
                    "give @s minecraft:mace", "give @s minecraft:wind_charge 16");
        }

        @Override
        public boolean countdown() {
            return false;
        }

        private void launch() {
            dive++;
            airborne = false;
            hitThisDive = false;
            diveTicks = 0;
            int x = random.nextInt(11) - 5;
            int z = random.nextInt(11) - 5;
            command("tp @s " + x + " " + HEIGHT + " " + z + " 0 90");
        }

        @Override
        public boolean tick() {
            if (dive == 0) {
                launch();
                return false;
            }
            diveTicks++;
            if (!airborne) {
                airborne = mc.player.getY() > HEIGHT - 5;
            } else if (mc.player.onGround() || diveTicks > 200) {
                if (++wait < 10) {
                    return false;
                }
                wait = 0;
                if (dive >= DIVES) {
                    return true;
                }
                launch();
            }
            return false;
        }

        @Override
        public void attacked(Entity target) {
            if (airborne && !hitThisDive && target instanceof LivingEntity && mc.player.fallDistance > 1.5) {
                hitThisDive = true;
                hits++;
                fallTotal += mc.player.fallDistance;
            }
        }

        @Override
        public List<String> status() {
            return List.of("Dive " + Math.max(1, dive) + " / " + DIVES, hits + " smashes");
        }

        @Override
        public float score() {
            return hits;
        }

        @Override
        public String format(float score) {
            return Math.round(score) + " / " + DIVES + " smashes";
        }

        @Override
        public List<String> details() {
            return hits == 0 ? List.of("No smashes landed") : List.of(String.format("Average fall %.1f blocks", fallTotal / hits));
        }
    }

    /** Ride a spear charge through a line of targets: charged hits count, jabs don't. */
    static final class SpearCharge extends Drill {
        private static final int TICKS = 30 * 20;
        private static final int[] ROWS = {6, 12, 18, 24};
        private int ticks;
        private int charged;
        private int jabs;
        private final java.util.Map<Entity, Integer> lastHurt = new java.util.WeakHashMap<Entity, Integer>();

        SpearCharge() {
            super("spear_charge", "Spear Charge", "Hold right-click with the spear and run through the targets for 30 seconds", "timer", false);
        }

        @Override
        public List<String> setup() {
            List<String> c = new ArrayList<String>();
            c.add("fill -3 99 -2 3 99 30 minecraft:smooth_stone");
            for (int z : ROWS) {
                c.add("summon minecraft:husk 0 100 " + z + " " + TARGET);
            }
            c.add("tp @s 0 100 -1 0 0");
            c.add("give @s minecraft:netherite_spear");
            c.add("effect give @s minecraft:speed infinite 1 true");
            return c;
        }

        @Override
        public boolean tick() {
            ItemStack using = mc.player.isUsingItem() ? mc.player.getUseItem() : ItemStack.EMPTY;
            boolean spear = !using.isEmpty() && BuiltInRegistries.ITEM.getKey(using.getItem()).getPath().endsWith("_spear");
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof LivingEntity t && e != mc.player) {
                    int before = lastHurt.getOrDefault(e, 0);
                    if (t.hurtTime > before) {
                        if (spear) {
                            charged++;
                        } else {
                            jabs++;
                        }
                    }
                    lastHurt.put(e, t.hurtTime);
                }
            }
            if (mc.player.getZ() > 29) {
                command("tp @s 0 100 -1 0 0");
            }
            return ++ticks >= TICKS;
        }

        @Override
        public List<String> status() {
            return List.of(charged + " charged hits", String.format("%.1f s left", (TICKS - ticks) / 20f));
        }

        @Override
        public float score() {
            return charged;
        }

        @Override
        public String format(float score) {
            return Math.round(score) + " charged hits";
        }

        @Override
        public List<String> details() {
            return List.of(jabs + " jabs", "Reach the end and you go back to the start");
        }
    }
}
