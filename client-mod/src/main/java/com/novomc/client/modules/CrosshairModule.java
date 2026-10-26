package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.util.Draw;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Replaces the vanilla crosshair with a configurable one. */
public class CrosshairModule extends Module {
    public CrosshairModule() {
        super("crosshair");
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Pre event) {
        if (!isEnabled() || event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }
        event.setCanceled(true);
        if (mc.gameSettings.thirdPersonView != 0) {
            return;
        }
        int cx = event.resolution.getScaledWidth() / 2;
        int cy = event.resolution.getScaledHeight() / 2;
        int size = integer("size");
        int gap = integer("gap");
        int t = integer("thickness");
        String style = choice("style");
        int color = color("color");
        boolean outline = bool("outline");

        int lo = -(t - 1) / 2;
        int hi = lo + t;
        if (!"dot".equals(style)) {
            bar(cx - gap - size, cy + lo, cx - gap, cy + hi, color, outline);
            bar(cx + 1 + gap, cy + lo, cx + 1 + gap + size, cy + hi, color, outline);
            bar(cx + lo, cy + 1 + gap, cx + hi, cy + 1 + gap + size, color, outline);
            if (!"t".equals(style)) {
                bar(cx + lo, cy - gap - size, cx + hi, cy - gap, color, outline);
            }
        }
        if ("dot".equals(style) || bool("centerDot")) {
            bar(cx + lo, cy + lo, cx + hi + ("dot".equals(style) ? 1 : 0), cy + hi + ("dot".equals(style) ? 1 : 0), color, outline);
        }
    }

    private static void bar(int l, int t, int r, int b, int color, boolean outline) {
        if (outline) {
            Draw.rect(l - 1, t - 1, r + 1, b + 1, 0xC0000000);
        }
        Draw.rect(l, t, r, b, color);
    }
}
