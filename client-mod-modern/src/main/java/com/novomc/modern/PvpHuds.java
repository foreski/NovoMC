package com.novomc.modern;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The second batch of HUD mods on modern, ported from 1.8.9 (same ids, settings and look). */
public final class PvpHuds {
    private PvpHuds() {}

    private static Entity attacked;
    private static long attackedAt;
    private static double reach;

    /** Records the local player's attacks for Reach Display and Combo Counter. */
    static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (level.isClientSide() && player == mc.player) {
                Vec3 eyes = player.getEyePosition(1f);
                if (mc.hitResult instanceof EntityHitResult e && e.getEntity() == target) {
                    reach = e.getLocation().distanceTo(eyes);
                } else {
                    // Closest point of the target's hitbox.
                    AABB b = target.getBoundingBox();
                    reach = eyes.distanceTo(new Vec3(Mth.clamp(eyes.x, b.minX, b.maxX), Mth.clamp(eyes.y, b.minY, b.maxY),
                            Mth.clamp(eyes.z, b.minZ, b.maxZ)));
                }
                attacked = target;
                attackedAt = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });
    }

    public static final class Direction extends HudModule {
        private static final int W = 140;
        private static final int H = 18;
        private static final float SPAN = 180f;
        private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

        public Direction() {
            super("direction");
        }

        @Override
        public int width() {
            return W;
        }

        @Override
        public int height() {
            return H;
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            float yaw = mc.player != null ? Mth.wrapDegrees(mc.player.getYRot()) : 180f;
            int marker = color("markerColor");
            int text = color("textColor");
            g.fill(0, 0, W, H, 0x6F000000);
            for (int deg = -180; deg < 180; deg += 15) {
                float diff = Mth.wrapDegrees(deg - yaw);
                if (Math.abs(diff) > SPAN / 2 - 4) {
                    continue;
                }
                int x = Math.round(W / 2f + diff * W / SPAN);
                if (deg % 45 == 0) {
                    String label = LABELS[((deg + 360) % 360) / 45];
                    int c = "N".equals(label) ? marker : label.length() == 1 ? text : (text & 0xFFFFFF) | 0xAA000000;
                    g.text(mc.font, label, x - mc.font.width(label) / 2, 5, c, true);
                } else {
                    g.fill(x, H - 5, x + 1, H - 2, (text & 0xFFFFFF) | 0x66000000);
                }
            }
            g.fill(W / 2, 0, W / 2 + 1, 3, marker);
            g.fill(W / 2, H - 2, W / 2 + 1, H, marker);
        }
    }

    public static final class Saturation extends TextHud {
        public Saturation() {
            super("saturation");
        }

        @Override
        protected String[] lines() {
            float sat = mc.player != null ? mc.player.getFoodData().getSaturationLevel() : 5f;
            return new String[] {String.format("Saturation %.1f", sat)};
        }
    }

    /** Counts of PvP items in the inventory: totems, crystals, pearls, gapples, arrows and the held item. */
    public static final class ItemCounter extends HudModule {
        private static final int ROW = 17;

        public ItemCounter() {
            super("item_counter");
        }

        private int count(Item item) {
            int n = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(item)) {
                    n += s.getCount();
                }
            }
            return n;
        }

        private List<Object[]> rows() {
            List<Object[]> out = new ArrayList<Object[]>();
            if (mc.player == null) {
                return out;
            }
            add(out, "totems", Items.TOTEM_OF_UNDYING);
            add(out, "crystals", Items.END_CRYSTAL);
            add(out, "windCharges", Items.WIND_CHARGE);
            add(out, "enchantedApples", Items.ENCHANTED_GOLDEN_APPLE);
            addHealing(out);
            add(out, "anchors", Items.RESPAWN_ANCHOR);
            add(out, "anchors", Items.GLOWSTONE);
            add(out, "enderPearls", Items.ENDER_PEARL);
            add(out, "goldenApples", Items.GOLDEN_APPLE);
            add(out, "arrows", Items.ARROW);
            ItemStack held = mc.player.getMainHandItem();
            if (bool("heldItem") && !held.isEmpty()) {
                out.add(new Object[] {held, count(held.getItem())});
            }
            return out;
        }

        /** Healing potions (drinkable or splash), counted together. */
        private void addHealing(List<Object[]> out) {
            if (!def.defaults.containsKey("potions") || !bool("potions")) {
                return;
            }
            int n = 0;
            ItemStack icon = null;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                PotionContents p = s.get(DataComponents.POTION_CONTENTS);
                if (p != null && (p.is(Potions.HEALING) || p.is(Potions.STRONG_HEALING))) {
                    n += s.getCount();
                    icon = icon == null || s.is(Items.SPLASH_POTION) ? s : icon;
                }
            }
            if (n > 0) {
                out.add(new Object[] {icon, n});
            }
        }

        private void add(List<Object[]> out, String key, Item item) {
            if (def.defaults.containsKey(key) && bool(key)) {
                int n = count(item);
                if (n > 0) {
                    out.add(new Object[] {new ItemStack(item), n});
                }
            }
        }

        @Override
        public boolean visible() {
            return !rows().isEmpty();
        }

        @Override
        public int width() {
            int w = 16;
            for (Object[] r : rows()) {
                w = Math.max(w, 18 + mc.font.width(String.valueOf(r[1])));
            }
            return w;
        }

        @Override
        public int height() {
            return Math.max(1, rows().size()) * ROW - 1;
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            int y = 0;
            for (Object[] r : rows()) {
                g.item((ItemStack) r[0], 0, y);
                g.text(mc.font, String.valueOf(r[1]), 18, y + 4, color("textColor"), true);
                y += ROW;
            }
        }
    }

    /**
     * Hits in a row: a hit counts when your target shows its hurt animation right
     * after you attack; getting hurt yourself or 2.5 s without a hit resets it.
     */
    public static final class ComboCounter extends TextHud {
        private int combo;
        private long lastHit;
        private long seenAttack;
        private boolean counted;
        private int lastOwnHurt;

        public ComboCounter() {
            super("combo_counter");
        }

        @Override
        public void tick() {
            if (mc.player == null) {
                combo = 0;
                return;
            }
            long now = System.currentTimeMillis();
            if (attackedAt != seenAttack) {
                seenAttack = attackedAt;
                counted = false;
            }
            int hurt = mc.player.hurtTime;
            if (hurt > lastOwnHurt) {
                combo = 0;
            }
            lastOwnHurt = hurt;
            if (attacked instanceof LivingEntity t && !counted && now - attackedAt < 500 && t.hurtDuration > 0 && t.hurtTime > t.hurtDuration - 3) {
                combo++;
                lastHit = now;
                counted = true;
            }
            if (now - lastHit > 2500) {
                combo = 0;
            }
        }

        @Override
        public boolean visible() {
            return combo > 0;
        }

        @Override
        protected String[] lines() {
            return new String[] {combo + " Combo"};
        }
    }

    public static final class ReachDisplay extends TextHud {
        private static final long SHOW_MS = 5000;

        public ReachDisplay() {
            super("reach_display");
        }

        @Override
        public boolean visible() {
            return attackedAt > 0 && System.currentTimeMillis() - attackedAt < SHOW_MS;
        }

        @Override
        protected String[] lines() {
            return new String[] {String.format("%.2f blocks", reach)};
        }
    }

    public static final class ServerAddress extends TextHud {
        public ServerAddress() {
            super("server_address");
        }

        @Override
        public boolean visible() {
            return mc.getCurrentServer() != null || bool("showInSingleplayer");
        }

        @Override
        protected String[] lines() {
            ServerData s = mc.getCurrentServer();
            return new String[] {s != null ? s.ip : "Singleplayer"};
        }
    }

    public static final class Playtime extends TextHud {
        private long joinedAt;

        public Playtime() {
            super("playtime");
        }

        @Override
        public void tick() {
            if (mc.level == null) {
                joinedAt = 0;
            } else if (joinedAt == 0) {
                joinedAt = System.currentTimeMillis();
            }
        }

        @Override
        protected String[] lines() {
            long s = joinedAt == 0 ? 0 : (System.currentTimeMillis() - joinedAt) / 1000;
            return new String[] {String.format("Playtime %d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)};
        }
    }

    public static final class DayCounter extends TextHud {
        public DayCounter() {
            super("day_counter");
        }

        @Override
        protected String[] lines() {
            long day = mc.level != null ? mc.level.getGameTime() / 24000 + 1 : 1;
            return new String[] {"Day " + day};
        }
    }
}
