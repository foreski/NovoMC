package com.novomc.modern;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The modern client's HUD mods, matching their 1.8.9 versions. */
public final class Huds {
    private Huds() {}

    public static final class Fps extends TextHud {
        public Fps() {
            super("fps");
        }

        @Override
        protected String[] lines() {
            return new String[] {mc.getFps() + " FPS"};
        }
    }

    public static final class Cps extends TextHud {
        public Cps() {
            super("cps");
        }

        @Override
        protected String[] lines() {
            return new String[] {bool("showRight") ? Clicks.left() + " | " + Clicks.right() + " CPS" : Clicks.left() + " CPS"};
        }
    }

    public static final class Coordinates extends TextHud {
        public Coordinates() {
            super("coordinates");
        }

        @Override
        protected String[] lines() {
            if (mc.player == null) {
                return new String[] {"X: 0", "Y: 64", "Z: 0"};
            }
            int d = Math.max(0, Math.min(3, Math.round(number("decimals"))));
            String f = "%." + d + "f";
            List<String> out = new ArrayList<String>();
            out.add("X: " + String.format(f, mc.player.getX()));
            out.add("Y: " + String.format(f, mc.player.getY()));
            out.add("Z: " + String.format(f, mc.player.getZ()));
            if (bool("showFacing")) {
                Direction dir = mc.player.getDirection();
                String axis = switch (dir) {
                    case NORTH -> "(-Z)";
                    case SOUTH -> "(+Z)";
                    case WEST -> "(-X)";
                    default -> "(+X)";
                };
                String name = dir.getName();
                out.add(Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + axis);
            }
            return out.toArray(new String[0]);
        }
    }

    public static final class Ping extends TextHud {
        public Ping() {
            super("ping");
        }

        @Override
        public boolean visible() {
            return bool("showInSingleplayer") || !mc.isLocalServer();
        }

        @Override
        protected String[] lines() {
            PlayerInfo info = mc.player == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return new String[] {(info == null ? 0 : info.getLatency()) + " ms"};
        }
    }

    public static final class Clock extends TextHud {
        public Clock() {
            super("clock");
        }

        @Override
        protected String[] lines() {
            String pattern = bool("format24h") ? (bool("showSeconds") ? "HH:mm:ss" : "HH:mm") : (bool("showSeconds") ? "h:mm:ss a" : "h:mm a");
            return new String[] {LocalTime.now().format(DateTimeFormatter.ofPattern(pattern))};
        }
    }

    public static final class Memory extends TextHud {
        public Memory() {
            super("memory");
        }

        @Override
        protected String[] lines() {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            long max = rt.maxMemory() >> 20;
            return new String[] {"Mem " + used * 100 / Math.max(1, max) + "% (" + used + "/" + max + " MB)"};
        }
    }

    /** Movement keys, mouse buttons with CPS and the space bar. */
    public static final class Keystrokes extends HudModule {
        private static final int K = 22;
        private static final int GAP = 2;
        private static final int W = K * 3 + GAP * 2;

        public Keystrokes() {
            super("keystrokes");
        }

        @Override
        public int width() {
            return W;
        }

        @Override
        public int height() {
            int h = K * 2 + GAP;
            if (bool("showMouse")) {
                h += GAP + K;
            }
            if (bool("showSpace")) {
                h += GAP + 10;
            }
            return h;
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            key(g, mc.options.keyUp, K + GAP, 0, K, K, null);
            key(g, mc.options.keyLeft, 0, K + GAP, K, K, null);
            key(g, mc.options.keyDown, K + GAP, K + GAP, K, K, null);
            key(g, mc.options.keyRight, (K + GAP) * 2, K + GAP, K, K, null);
            int y = (K + GAP) * 2;
            if (bool("showMouse")) {
                int half = (W - GAP) / 2;
                boolean cps = bool("showCps");
                key(g, mc.options.keyAttack, 0, y, half, K, cps ? Clicks.left() + " CPS" : "LMB");
                key(g, mc.options.keyUse, half + GAP, y, half, K, cps ? Clicks.right() + " CPS" : "RMB");
                y += K + GAP;
            }
            if (bool("showSpace")) {
                key(g, mc.options.keyJump, 0, y, W, 10, "");
            }
        }

        private void key(GuiGraphicsExtractor g, KeyMapping key, int x, int y, int w, int h, String label) {
            boolean down = key.isDown();
            g.fill(x, y, x + w, y + h, down ? (color("pressedColor") & 0x00FFFFFF) | 0x99000000 : 0x6F000000);
            String text = label != null ? label : key.getTranslatedKeyMessage().getString();
            if (!text.isEmpty()) {
                int c = down ? 0xFF000000 : color("textColor");
                g.text(mc.font, text, x + (w - mc.font.width(text)) / 2, y + (h - 8) / 2, c, !down);
            }
        }
    }

    /** Worn armor and the held item, with durability. */
    public static final class ArmorStatus extends HudModule {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        public ArmorStatus() {
            super("armor_status");
        }

        private List<ItemStack> items() {
            List<ItemStack> out = new ArrayList<ItemStack>();
            if (mc.player == null) {
                return out;
            }
            for (EquipmentSlot slot : SLOTS) {
                ItemStack s = mc.player.getItemBySlot(slot);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
            if (bool("showHeld") && !mc.player.getMainHandItem().isEmpty()) {
                out.add(mc.player.getMainHandItem());
            }
            return out;
        }

        @Override
        public int width() {
            return 18 + 40;
        }

        @Override
        public int height() {
            return Math.max(1, items().size()) * 18;
        }

        @Override
        public void render(GuiGraphicsExtractor g) {
            List<ItemStack> items = items();
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                g.item(s, 0, i * 18);
                String text = durability(s);
                if (text != null) {
                    g.text(mc.font, text, 20, i * 18 + 5, color("textColor"), true);
                }
            }
        }

        private String durability(ItemStack s) {
            if (!s.isDamageableItem()) {
                return s.getCount() > 1 ? String.valueOf(s.getCount()) : null;
            }
            int left = s.getMaxDamage() - s.getDamageValue();
            return switch (choice("durability")) {
                case "percent" -> Math.round(100f * left / s.getMaxDamage()) + "%";
                case "none" -> null;
                default -> String.valueOf(left);
            };
        }
    }

    /** Active effects with level and time left. */
    public static final class PotionEffects extends TextHud {
        public PotionEffects() {
            super("potion_effects");
        }

        @Override
        public boolean visible() {
            return mc.player != null && !mc.player.getActiveEffects().isEmpty();
        }

        @Override
        protected String[] lines() {
            List<String> out = new ArrayList<String>();
            if (mc.player != null) {
                for (MobEffectInstance e : mc.player.getActiveEffects()) {
                    String name = e.getEffect().value().getDisplayName().getString();
                    String level = e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "";
                    String time = e.isInfiniteDuration() ? "∞" : String.format("%d:%02d", e.getDuration() / 1200, e.getDuration() / 20 % 60);
                    boolean blink = bool("blinkWhenLow") && !e.isInfiniteDuration() && e.getDuration() < 200 && System.currentTimeMillis() / 400 % 2 == 0;
                    out.add(name + level + (blink ? "" : "  " + time));
                }
            }
            return out.isEmpty() ? new String[] {"Speed II  1:30"} : out.toArray(new String[0]);
        }

        private static String roman(int n) {
            String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
            return n >= 1 && n <= 10 ? r[n - 1] : String.valueOf(n);
        }
    }
}
