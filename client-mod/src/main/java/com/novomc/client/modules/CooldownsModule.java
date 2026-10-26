package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Countdowns after throwing a pearl or eating a golden apple. 1.8.9 has no
 * item cooldowns itself; servers add their own, so the durations are settings.
 */
public class CooldownsModule extends HudModule {
    private static final int ROW = 17;
    private long pearlUntil;
    private long gappleUntil;

    public CooldownsModule() {
        super("cooldowns");
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled() || event.entityPlayer != mc.thePlayer || event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        long now = System.currentTimeMillis();
        if (held != null && held.getItem() == Items.ender_pearl && integer("pearl") > 0 && now >= pearlUntil) {
            pearlUntil = now + integer("pearl") * 1000L;
        }
    }

    @SubscribeEvent
    public void onFinishUsing(PlayerUseItemEvent.Finish event) {
        if (isEnabled() && event.entityPlayer == mc.thePlayer && event.item != null
                && event.item.getItem() == Items.golden_apple && integer("gapple") > 0) {
            gappleUntil = System.currentTimeMillis() + integer("gapple") * 1000L;
        }
    }

    /** {item, seconds left} pairs. */
    private List<Object[]> rows(boolean preview) {
        long now = System.currentTimeMillis();
        List<Object[]> out = new ArrayList<Object[]>();
        if (pearlUntil > now) {
            out.add(new Object[] {Items.ender_pearl, (pearlUntil - now) / 1000f});
        }
        if (gappleUntil > now) {
            out.add(new Object[] {Items.golden_apple, (gappleUntil - now) / 1000f});
        }
        if (out.isEmpty() && preview) {
            out.add(new Object[] {Items.ender_pearl, 12.4f});
        }
        return out;
    }

    private static String label(Object[] row) {
        return String.format("%.1fs", (Float) row[1]);
    }

    @Override
    public boolean visible() {
        return !rows(false).isEmpty();
    }

    @Override
    public int width(boolean preview) {
        int w = 16;
        for (Object[] r : rows(preview)) {
            w = Math.max(w, 18 + Draw.width(label(r)));
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
            Draw.item(new ItemStack((Item) r[0]), 0, y);
            Draw.text(label(r), 18, y + 4, color("textColor"));
            y += ROW;
        }
    }
}
