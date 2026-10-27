package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** A pulsing red glow around the screen edges while your health is low. */
public class LowHealthModule extends Module {
    /** Whether the warning showed on the last frame (for the dev mod check). */
    public boolean showing;

    public LowHealthModule() {
        super("low_health");
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        float hearts = mc.thePlayer != null ? mc.thePlayer.getHealth() / 2f : 99;
        showing = isEnabled() && mc.thePlayer != null && !mc.thePlayer.capabilities.isCreativeMode && hearts > 0 && hearts <= number("hearts");
        if (!showing) {
            return;
        }
        // Stronger the closer you are to dying, and pulsing.
        float danger = 1f - (hearts - 0.5f) / Math.max(1f, number("hearts"));
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        int alpha = Math.round(Math.min(1f, 0.35f + 0.65f * danger) * pulse * number("intensity") / 100f * 255);
        int rgb = color("color") & 0xFFFFFF;
        ScaledResolution sr = event.resolution;
        vignette(sr.getScaledWidth(), sr.getScaledHeight(), rgb, Math.max(0, Math.min(255, alpha)));
    }

    /** Edges at `alpha`, fading to transparent toward the middle. */
    private static void vignette(int w, int h, int rgb, int alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float edge = Math.min(w, h) * 0.22f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // top, bottom, left, right bands: opaque at the edge, clear inward.
        quad(wr, 0, 0, w, 0, w, edge, 0, edge, r, g, b, alpha, alpha, 0, 0);
        quad(wr, 0, h - edge, w, h - edge, w, h, 0, h, r, g, b, 0, 0, alpha, alpha);
        quad(wr, 0, 0, edge, 0, edge, h, 0, h, r, g, b, alpha, 0, 0, alpha);
        quad(wr, w - edge, 0, w, 0, w, h, w - edge, h, r, g, b, 0, alpha, alpha, 0);
        t.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    // Counter-clockwise on screen, like Gui.drawRect.
    private static void quad(WorldRenderer wr, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
            int r, int g, int b, int a1, int a2, int a3, int a4) {
        wr.pos(x4, y4, 0).color(r, g, b, a4).endVertex();
        wr.pos(x3, y3, 0).color(r, g, b, a3).endVertex();
        wr.pos(x2, y2, 0).color(r, g, b, a2).endVertex();
        wr.pos(x1, y1, 0).color(r, g, b, a1).endVertex();
    }
}
