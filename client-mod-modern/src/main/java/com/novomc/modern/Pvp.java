package com.novomc.modern;

import com.novomc.modern.ui.NovoUi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Mods for modern (1.9+) PvP: Attack Cooldown, Totem Pop Counter, Target HUD,
 * Armor Durability Alert and Mace / Elytra HUD, plus the 1.8.9 ports Hit
 * Sounds, Particle Changer, TNT Countdown and Tab Editor.
 */
public final class Pvp {
    private Pvp() {}

    private static <T extends Module> T on(Class<T> type) {
        for (Module m : NovoModern.modules()) {
            if (type.isInstance(m) && m.isEnabled()) {
                return type.cast(m);
            }
        }
        return null;
    }

    /** Hits by the local player, for Target HUD, Hit Sounds and Particle Changer. */
    static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (level.isClientSide() && player == mc.player && target instanceof LivingEntity living) {
                boolean crit = critReady(mc.player);
                TargetHud t = on(TargetHud.class);
                if (t != null) {
                    t.hit(living);
                }
                HitSounds s = on(HitSounds.class);
                if (s != null) {
                    s.hit(living);
                }
                ParticleChanger p = on(ParticleChanger.class);
                if (p != null) {
                    p.hit(living, crit);
                }
            }
            return InteractionResult.PASS;
        });
    }

    /** Whether a hit now would be a critical hit (vanilla's rules, charge included). */
    static boolean critReady(Player p) {
        return p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.hasEffect(MobEffects.BLINDNESS)
                && !p.isPassenger() && !p.isSprinting() && p.getAttackStrengthScale(0.5f) > 0.9f;
    }

    /** A charge bar under the crosshair; gold with "CRIT" when the next hit would crit. */
    public static final class AttackCooldown extends HudModule {
        private static final int W = 32;

        public AttackCooldown() {
            super("attack_cooldown");
        }

        private float charge() {
            return mc.player == null ? 1f : mc.player.getAttackStrengthScale(0f);
        }

        @Override
        public boolean visible() {
            return mc.player != null && !(bool("hideWhenFull") && charge() >= 1f && !crit());
        }

        private boolean crit() {
            return bool("critIndicator") && mc.player != null && critReady(mc.player);
        }

        @Override
        public int width() {
            return W;
        }

        @Override
        public int height() {
            return 12;
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            float c = charge();
            boolean crit = crit();
            int color = crit ? 0xFFFACC15 : c >= 1f ? color("color") : NovoUi.withAlpha(color("color"), 0xA0);
            g.fill(0, 0, W, 3, 0x90000000);
            g.fill(0, 0, Math.round(W * c), 3, color);
            if (crit) {
                String s = "CRIT";
                g.text(mc.font, s, (W - mc.font.width(s)) / 2, 4, 0xFFFACC15, true);
            }
        }
    }

    /** Counts each player's totem pops this life: chat line and a "-N" next to their nametag (LivingEntityMixin). */
    public static final class TotemPops extends Module {
        private static final Map<UUID, Integer> POPS = new ConcurrentHashMap<UUID, Integer>();

        public TotemPops() {
            super("totem_pops");
        }

        public static void popped(LivingEntity entity) {
            TotemPops t = on(TotemPops.class);
            if (t == null || !(entity instanceof Player p)) {
                return;
            }
            int n = POPS.merge(p.getUUID(), 1, Integer::sum);
            Minecraft mc = Minecraft.getInstance();
            if (t.bool("chat") && mc.player != null) {
                String who = p == mc.player ? "You" : p.getName().getString();
                mc.player.sendSystemMessage(Component.literal("[Novo] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(who + " popped " + n + (n == 1 ? " totem" : " totems")).withStyle(ChatFormatting.WHITE)));
            }
        }

        public static void died(LivingEntity entity) {
            POPS.remove(entity.getUUID());
        }

        /** The nametag with " -N" appended, or the vanilla one. */
        public static Component nametag(Entity entity, Component vanilla) {
            TotemPops t = on(TotemPops.class);
            Integer n = vanilla == null ? null : POPS.get(entity.getUUID());
            if (t == null || n == null || !t.bool("nametag")) {
                return vanilla;
            }
            return vanilla.copy().append(Component.literal(" -" + n).withStyle(ChatFormatting.GOLD));
        }

        @Override
        public void tick() {
            if (mc.level == null) {
                POPS.clear();
            }
        }
    }

    /** The entity you last hit: head, name, health (with absorption), armor and distance. */
    public static final class TargetHud extends HudModule {
        private static final int W = 120;
        private LivingEntity target;
        private long hitAt;

        public TargetHud() {
            super("target_hud");
        }

        void hit(LivingEntity e) {
            target = e;
            hitAt = System.currentTimeMillis();
        }

        @Override
        public boolean visible() {
            if (target == null || target.isRemoved() || System.currentTimeMillis() - hitAt > number("timeout") * 1000) {
                target = null;
                return false;
            }
            return true;
        }

        @Override
        public int width() {
            return W;
        }

        @Override
        public int height() {
            return bool("armor") ? 44 : 30;
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            LivingEntity t = target;
            if (t == null) {
                return;
            }
            int bg = Math.round(number("background") / 100f * 255) << 24;
            g.fill(0, 0, W, height(), bg | 0x0C1019);
            int tx = 4;
            if (t instanceof AbstractClientPlayer p) {
                PlayerFaceExtractor.extractRenderState(g, p.getSkin(), 4, 4, 22);
                tx = 30;
            }
            g.text(mc.font, t.getName().getString(), tx, 4, 0xFFFFFFFF, true);
            float health = t.getHealth() + t.getAbsorptionAmount();
            float max = Math.max(1, t.getMaxHealth());
            String hp = String.format("%.1f", health / 2f) + " ❤";
            g.text(mc.font, hp, W - 4 - mc.font.width(hp), 4, 0xFFFF5555, true);
            int bw = W - tx - 4;
            g.fill(tx, 16, tx + bw, 19, 0xFF2A3142);
            float frac = Math.min(1f, t.getHealth() / max);
            int hc = frac > 0.5f ? 0xFF4ADE80 : frac > 0.25f ? 0xFFFACC15 : 0xFFF87171;
            g.fill(tx, 16, tx + Math.round(bw * frac), 19, hc);
            if (t.getAbsorptionAmount() > 0) {
                g.fill(tx, 19, tx + Math.round(bw * Math.min(1f, t.getAbsorptionAmount() / max)), 20, 0xFFFACC15);
            }
            String dist = mc.player == null ? "" : String.format("%.1fm", mc.player.distanceTo(t));
            g.text(mc.font, dist, tx, 22, 0xFFAAB2C0, true);
            if (bool("armor")) {
                int x = 4;
                for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                    ItemStack s = t.getItemBySlot(slot);
                    if (!s.isEmpty()) {
                        g.item(s, x, 28);
                        g.itemDecorations(mc.font, s, x, 28);
                    }
                    x += 18;
                }
            }
        }
    }

    /** Warns when armor or the elytra is about to break, with a sound when one first crosses the line. */
    public static final class ArmorAlert extends HudModule {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        private final Map<ItemStack, Boolean> warned = new WeakHashMap<ItemStack, Boolean>();

        public ArmorAlert() {
            super("armor_alert");
        }

        private List<ItemStack> low() {
            List<ItemStack> out = new ArrayList<ItemStack>();
            if (mc.player == null) {
                return out;
            }
            float limit = number("threshold") / 100f;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack s = mc.player.getItemBySlot(slot);
                if (s.isDamageableItem() && 1f - s.getDamageValue() / (float) s.getMaxDamage() <= limit) {
                    out.add(s);
                }
            }
            return out;
        }

        @Override
        public void tick() {
            for (ItemStack s : low()) {
                if (warned.put(s, true) == null && bool("sound")) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1f));
                }
            }
        }

        @Override
        public boolean visible() {
            return !low().isEmpty();
        }

        @Override
        public int width() {
            int w = 0;
            for (ItemStack s : low()) {
                w = Math.max(w, 18 + mc.font.width(label(s)));
            }
            return Math.max(18, w);
        }

        @Override
        public int height() {
            return Math.max(1, low().size()) * 17 - 1;
        }

        private static String label(ItemStack s) {
            int pct = Math.round(100f * (1f - s.getDamageValue() / (float) s.getMaxDamage()));
            return s.getHoverName().getString() + " " + pct + "%";
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            // Blinks so it catches the eye.
            int color = (System.currentTimeMillis() / 400) % 2 == 0 ? 0xFFFF5555 : 0xFFFFAA55;
            int y = 0;
            for (ItemStack s : low()) {
                g.item(s, 0, y);
                g.text(mc.font, label(s), 18, y + 4, color, true);
                y += 17;
            }
        }
    }

    /** With a mace: fall distance and the smash bonus. Gliding: speed, height, fireworks and elytra durability. */
    public static final class MaceHud extends TextHud {
        public MaceHud() {
            super("mace_hud");
        }

        private boolean mace() {
            return bool("mace") && mc.player != null && mc.player.getMainHandItem().is(Items.MACE) && mc.player.fallDistance > 1.5;
        }

        private boolean elytra() {
            return bool("elytra") && mc.player != null && mc.player.isFallFlying();
        }

        @Override
        public boolean visible() {
            return mace() || elytra();
        }

        /** Vanilla's smash bonus before enchantments: 4 per block up to 3, then 2 up to 8, then 1. */
        static double smashBonus(double fall) {
            if (fall <= 1.5) {
                return 0;
            }
            return fall <= 3 ? 4 * fall : fall <= 8 ? 12 + 2 * (fall - 3) : 22 + (fall - 8);
        }

        @Override
        protected String[] lines() {
            List<String> out = new ArrayList<String>();
            if (mc.player == null) {
                return new String[] {""};
            }
            if (mace()) {
                double fall = mc.player.fallDistance;
                out.add(String.format("Fall %.1f  +%.0f smash", fall, smashBonus(fall)));
            }
            if (elytra()) {
                double speed = mc.player.getDeltaMovement().length() * 20;
                int rockets = 0;
                var inv = mc.player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).is(Items.FIREWORK_ROCKET)) {
                        rockets += inv.getItem(i).getCount();
                    }
                }
                ItemStack wings = mc.player.getItemBySlot(EquipmentSlot.CHEST);
                String dura = wings.isDamageableItem() ? Math.round(100f * (1f - wings.getDamageValue() / (float) wings.getMaxDamage())) + "%" : "-";
                out.add(String.format("%.1f b/s  Y %d", speed, Math.round(mc.player.getY())));
                out.add(rockets + " rockets  Elytra " + dura);
            }
            return out.isEmpty() ? new String[] {""} : out.toArray(new String[0]);
        }
    }

    /** A sound on each hit and another on a kill. */
    public static final class HitSounds extends Module {
        private static final int KILL_WINDOW = 60;
        private LivingEntity target;
        private int sinceHit;

        public HitSounds() {
            super("hit_sounds");
        }

        void hit(LivingEntity e) {
            play(sound(choice("sound")), 1f);
            target = e;
            sinceHit = 0;
        }

        /** 1.8.9's sound names, as stored in the shared config. */
        private static SoundEvent sound(String id) {
            return switch (id) {
                case "note.pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
                case "random.orb" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                case "random.click" -> SoundEvents.UI_BUTTON_CLICK.value();
                default -> SoundEvents.ARROW_HIT_PLAYER;
            };
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            if (++sinceHit > KILL_WINDOW) {
                target = null;
            } else if (target.isRemoved() || target.getHealth() <= 0) {
                if (bool("killSound")) {
                    play(SoundEvents.PLAYER_LEVELUP, 1f);
                }
                target = null;
            }
        }

        private void play(SoundEvent sound, float pitch) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, number("volume") / 100f));
        }
    }

    /** More crit and enchanted-hit particles on every hit, optionally even on non-crits. */
    public static final class ParticleChanger extends Module {
        public ParticleChanger() {
            super("particle_changer");
        }

        void hit(LivingEntity target, boolean crit) {
            int extra = Math.max(0, Math.round(number("multiplier")) - 1);
            boolean sharp = mc.player.getMainHandItem().isEnchanted();
            // Vanilla already spawns one burst when the hit really is a crit / enchanted hit.
            int crits = crit ? extra : bool("alwaysCrit") ? extra + 1 : 0;
            int magic = sharp ? extra : bool("alwaysSharpness") ? extra + 1 : 0;
            for (int i = 0; i < crits; i++) {
                mc.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
            }
            for (int i = 0; i < magic; i++) {
                mc.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
            }
        }
    }

    /** Seconds left over lit TNT, red to green (drawn as gizmos, see More.emitGizmos). */
    public static final class TntCountdown extends Module {
        private static final double RANGE = 48;

        public TntCountdown() {
            super("tnt_countdown");
        }

        static void emitIfOn(float pt) {
            TntCountdown t = on(TntCountdown.class);
            if (t != null) {
                t.emit(pt);
            }
        }

        private void emit(float pt) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof PrimedTnt tnt && e.distanceToSqr(mc.player) < RANGE * RANGE) {
                    float left = Math.max(0, (tnt.getFuse() - pt) / 20f);
                    int color = NovoUi.blend(0xFFFF5555, 0xFF55FF55, Math.min(1f, tnt.getFuse() / 80f));
                    Gizmos.billboardText(String.format("%.1f", left), e.getPosition(pt).add(0, e.getBbHeight() + 0.5, 0),
                            TextGizmo.Style.forColorAndCentered(color).withScale(TextGizmo.Style.DEFAULT_SCALE * 3));
                }
            }
        }
    }

    /** Tab list: ping as a coloured number, background opacity, and hiding the server's header/footer (PlayerTabOverlayMixin). */
    public static final class TabEditor extends Module {
        public TabEditor() {
            super("tab_editor");
        }

        public static TabEditor active() {
            return on(TabEditor.class);
        }

        public boolean pingNumbers() {
            return bool("pingNumbers");
        }

        public boolean hideHeader() {
            return bool("hideHeader");
        }

        public int background() {
            return Math.round(number("background") / 100f * 255) << 24;
        }

        public static void drawPing(GuiGraphicsExtractor g, int width, int x, int y, PlayerInfo info) {
            var font = Minecraft.getInstance().font;
            int ping = info.getLatency();
            String text = ping < 0 ? "?" : String.valueOf(ping);
            int color = ping < 0 ? 0xFFAAAAAA : ping < 75 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : ping < 300 ? 0xFFFFAA00 : 0xFFFF5555;
            g.text(font, text, x + width - font.width(text) - 1, y, color, true);
        }
    }
}
