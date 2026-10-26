package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Counts arrows, golden apples, pearls (and the held item) in the inventory. */
public class ItemCounterModule extends HudModule {
    private static final int ROW = 17;

    public ItemCounterModule() {
        super("item_counter");
    }

    /** Total of `item` in the main inventory; meta -1 matches any damage value. */
    private int count(Item item, int meta) {
        int n = 0;
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s != null && s.getItem() == item && (meta < 0 || s.getItemDamage() == meta)) {
                n += s.stackSize;
            }
        }
        return n;
    }

    /** {icon, count} pairs to show. */
    private List<Object[]> rows(boolean preview) {
        List<Object[]> out = new ArrayList<Object[]>();
        boolean live = mc.thePlayer != null;
        if (bool("arrows")) {
            out.add(new Object[] {new ItemStack(Items.arrow), live ? count(Items.arrow, -1) : 64});
        }
        if (bool("goldenApples")) {
            out.add(new Object[] {new ItemStack(Items.golden_apple), live ? count(Items.golden_apple, -1) : 12});
        }
        if (bool("enderPearls")) {
            out.add(new Object[] {new ItemStack(Items.ender_pearl), live ? count(Items.ender_pearl, -1) : 16});
        }
        if (bool("heldItem") && live && mc.thePlayer.getHeldItem() != null) {
            ItemStack held = mc.thePlayer.getHeldItem();
            out.add(new Object[] {held, count(held.getItem(), held.getHasSubtypes() ? held.getItemDamage() : -1)});
        }
        if (!preview) {
            for (int i = out.size() - 1; i >= 0; i--) {
                if ((Integer) out.get(i)[1] == 0) {
                    out.remove(i);
                }
            }
        }
        return out;
    }

    @Override
    public boolean visible() {
        return mc.thePlayer != null && !rows(false).isEmpty();
    }

    @Override
    public int width(boolean preview) {
        int w = 16;
        for (Object[] r : rows(preview)) {
            w = Math.max(w, 18 + Draw.width(String.valueOf(r[1])));
        }
        return w;
    }

    @Override
    public int height(boolean preview) {
        return Math.max(1, rows(preview).size()) * ROW - 1;
    }

    @Override
    public void render(boolean preview) {
        int y = 0;
        for (Object[] r : rows(preview)) {
            Draw.item((ItemStack) r[0], 0, y);
            Draw.text(String.valueOf(r[1]), 18, y + 4, color("textColor"));
            y += ROW;
        }
    }
}
