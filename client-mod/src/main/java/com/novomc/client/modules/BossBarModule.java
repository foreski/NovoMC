package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.boss.BossStatus;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** The boss health bar as a movable, resizable HUD module. */
public class BossBarModule extends HudModule {
    private static final int BAR_W = 182;

    public BossBarModule() {
        super("boss_bar");
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Pre event) {
        if (isEnabled() && event.type == RenderGameOverlayEvent.ElementType.BOSSHEALTH) {
            event.setCanceled(true);
        }
    }

    private boolean active() {
        return BossStatus.bossName != null && BossStatus.statusBarTime > 0;
    }

    @Override
    public boolean visible() {
        return active();
    }

    @Override
    public int width(boolean preview) {
        return BAR_W;
    }

    @Override
    public int height(boolean preview) {
        return (bool("showName") ? 10 : 0) + (bool("showBar") ? 5 : 0);
    }

    @Override
    public void render(boolean preview) {
        boolean real = active();
        String name = real ? BossStatus.bossName : "Ender Dragon";
        float health = real ? BossStatus.healthScale : 0.7f;
        if (real && !preview) {
            // Vanilla counts the bar down each frame it draws; we replaced that draw.
            --BossStatus.statusBarTime;
        }
        int y = 0;
        if (bool("showName")) {
            Draw.text(name, (BAR_W - Draw.width(name)) / 2f, 0, 0xFFFFFFFF);
            y = 10;
        }
        if (bool("showBar")) {
            mc.getTextureManager().bindTexture(Gui.icons);
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);
            Draw.GUI.drawTexturedModalRect(0, y, 0, 74, BAR_W, 5);
            int filled = (int) (health * (BAR_W + 1));
            if (filled > 0) {
                Draw.GUI.drawTexturedModalRect(0, y, 0, 79, filled, 5);
            }
        }
    }
}
