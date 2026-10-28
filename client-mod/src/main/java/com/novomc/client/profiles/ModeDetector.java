package com.novomc.client.profiles;

import java.util.Collection;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

/**
 * Works out which PvP mode the player is in, from what the client can see:
 * the sidebar scoreboard (minigame servers name their mode there) and,
 * for duels and practice servers, the kit in the inventory.
 */
public final class ModeDetector {
    public enum Mode {
        GENERAL("general", "General"),
        SWORD("sword", "Sword"),
        POT("pot", "Pot PvP"),
        UHC("uhc", "UHC"),
        BEDWARS("bedwars", "Bed Wars"),
        SKYWARS("skywars", "SkyWars"),
        BRIDGE("bridge", "The Bridge");

        public final String id;
        public final String label;

        Mode(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private ModeDetector() {}

    public static Mode detect(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return Mode.GENERAL;
        }
        Mode fromBoard = fromScoreboard(sidebarText(mc.theWorld.getScoreboard()));
        return fromBoard != null ? fromBoard : fromKit(mc.thePlayer.inventory);
    }

    /** Title and lines of the sidebar, upper case, without colour codes. */
    static String sidebarText(Scoreboard board) {
        ScoreObjective objective = board.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(objective.getDisplayName()).append('\n');
        Collection<Score> scores = board.getSortedScores(objective);
        for (Score score : scores) {
            String name = score.getPlayerName();
            text.append(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(name), name)).append('\n');
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(text.toString()).toUpperCase(Locale.ROOT);
    }

    /** Minigame names as servers print them in the sidebar; null if none matches. */
    static Mode fromScoreboard(String text) {
        if (text.contains("BED WARS") || text.contains("BEDWARS")) {
            return Mode.BEDWARS;
        }
        if (text.contains("SKYWARS") || text.contains("SKY WARS")) {
            return Mode.SKYWARS;
        }
        if (text.contains("BRIDGE")) {
            return Mode.BRIDGE;
        }
        if (text.contains("UHC")) {
            return Mode.UHC;
        }
        if (text.contains("NODEBUFF") || text.contains("NO DEBUFF") || text.contains("POT PVP")) {
            return Mode.POT;
        }
        return null;
    }

    /** Duels and practice: tell the kit apart by what it carries. */
    static Mode fromKit(InventoryPlayer inv) {
        int healPots = 0;
        boolean sword = false;
        boolean lava = false;
        boolean gapple = false;
        boolean bowOrRod = false;
        for (ItemStack stack : inv.mainInventory) {
            if (stack == null) {
                continue;
            }
            Item item = stack.getItem();
            if (item instanceof ItemSword) {
                sword = true;
            } else if (item == Items.lava_bucket) {
                lava = true;
            } else if (item == Items.golden_apple) {
                gapple = true;
            } else if (item instanceof ItemBow || item == Items.fishing_rod) {
                bowOrRod = true;
            } else if (item instanceof ItemPotion && ItemPotion.isSplash(stack.getMetadata()) && heals((ItemPotion) item, stack)) {
                healPots += stack.stackSize;
            }
        }
        if (healPots >= 4) {
            return Mode.POT;
        }
        if (sword && (lava || gapple && bowOrRod)) {
            return Mode.UHC;
        }
        // A sword alone: boxing, sumo, combo and classic sword duels. Lobbies hand out compasses and menus, not swords.
        return sword ? Mode.SWORD : Mode.GENERAL;
    }

    private static boolean heals(ItemPotion potion, ItemStack stack) {
        java.util.List<PotionEffect> effects = potion.getEffects(stack);
        if (effects == null) {
            return false;
        }
        for (PotionEffect e : effects) {
            if (e.getPotionID() == Potion.heal.id) {
                return true;
            }
        }
        return false;
    }
}
