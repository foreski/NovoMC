package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Armor (helmet to boots) and the held item, with durability. */
public class ArmorStatusModule extends HudModule {
    private static final int ROW = 17;

    public ArmorStatusModule() {
        super("armor_status");
    }

    private List<ItemStack> items(boolean preview) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (mc.thePlayer != null) {
            for (int i = 3; i >= 0; i--) {
                ItemStack s = mc.thePlayer.inventory.armorInventory[i];
                if (s != null) {
                    out.add(s);
                }
            }
            if (bool("showHeld") && mc.thePlayer.getHeldItem() != null) {
                out.add(mc.thePlayer.getHeldItem());
            }
        }
        if (out.isEmpty() && preview) {
            out.add(new ItemStack(Items.diamond_helmet));
            out.add(new ItemStack(Items.diamond_chestplate));
            out.add(new ItemStack(Items.diamond_leggings));
            out.add(new ItemStack(Items.diamond_boots));
            out.add(new ItemStack(Items.diamond_sword));
        }
        return out;
    }

    private String label(ItemStack s) {
        String mode = choice("durability");
        if (s.isItemStackDamageable()) {
            int left = s.getMaxDamage() - s.getItemDamage();
            if ("percent".equals(mode)) {
                return left * 100 / s.getMaxDamage() + "%";
            }
            return "none".equals(mode) ? "" : String.valueOf(left);
        }
        return s.stackSize > 1 ? String.valueOf(s.stackSize) : "";
    }

    private int labelColor(ItemStack s) {
        if (s.isItemStackDamageable()) {
            float f = 1f - s.getItemDamage() / (float) s.getMaxDamage();
            if (f < 0.25f) {
                return 0xFFFF5555;
            }
            if (f < 0.5f) {
                return 0xFFFFDD55;
            }
        }
        return color("textColor");
    }

    @Override
    public boolean visible() {
        return !items(false).isEmpty();
    }

    @Override
    public int width(boolean preview) {
        int w = 16;
        for (ItemStack s : items(preview)) {
            String l = label(s);
            if (!l.isEmpty()) {
                w = Math.max(w, 18 + Draw.width(l));
            }
        }
        return w;
    }

    @Override
    public int height(boolean preview) {
        return Math.max(1, items(preview).size()) * ROW - 1;
    }

    @Override
    public void render(boolean preview) {
        int y = 0;
        for (ItemStack s : items(preview)) {
            Draw.item(s, 0, y);
            String l = label(s);
            if (!l.isEmpty()) {
                Draw.text(l, 18, y + 4, labelColor(s));
            }
            y += ROW;
        }
    }
}
