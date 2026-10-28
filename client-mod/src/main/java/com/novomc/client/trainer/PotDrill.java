package com.novomc.client.trainer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

/**
 * Pot Refill: your inventory is full of Splash Healing II; refill the eight
 * empty hotbar slots as fast as you can. The clock starts when you open your
 * inventory.
 */
public final class PotDrill extends Drill {
    /** Splash Potion of Healing II. */
    private static final int HEALING_II_SPLASH = 16421;

    private long startedAt;
    private long finishedAt;

    public PotDrill() {
        super("pots", "Pot Refill", "Refill your hotbar with splash potions from your inventory, fast.", "flask-conical", true);
    }

    @Override
    public void setup(WorldServer world, EntityPlayerMP player) {
        clear(world);
        floor(world, -2, -2, 2, 2, Blocks.stained_hardened_clay.getStateFromMeta(9));
        player.inventory.clear();
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.diamond_sword));
        for (int slot = 9; slot < 36; slot++) {
            player.inventory.setInventorySlotContents(slot, new ItemStack(Items.potionitem, 1, HEALING_II_SPLASH));
        }
        player.inventoryContainer.detectAndSendChanges();
        player.playerNetServerHandler.setPlayerLocation(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5, 0, 0);
        startedAt = 0;
        finishedAt = 0;
    }

    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return false;
        }
        if (startedAt == 0 && mc.currentScreen instanceof GuiInventory) {
            startedAt = System.currentTimeMillis();
        }
        if (startedAt != 0 && filled() == 8) {
            finishedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private static int filled() {
        ItemStack[] inv = Minecraft.getMinecraft().thePlayer.inventory.mainInventory;
        int n = 0;
        for (int slot = 1; slot < 9; slot++) {
            if (inv[slot] != null && inv[slot].getItem() instanceof ItemPotion) {
                n++;
            }
        }
        return n;
    }

    private float elapsed() {
        long end = finishedAt != 0 ? finishedAt : System.currentTimeMillis();
        return startedAt == 0 ? 0 : (end - startedAt) / 1000f;
    }

    @Override
    public List<String> status() {
        return Arrays.asList(startedAt == 0 ? "Open your inventory to start" : seconds(elapsed()),
                (Minecraft.getMinecraft().thePlayer == null ? 0 : filled()) + " / 8 slots");
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
        return Collections.singletonList(String.format("%.2f s per pot", elapsed() / 8));
    }
}
