package com.novomc.client.gui;

import com.novomc.client.NovoMC;
import com.novomc.client.util.Draw;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * NovoMC's loading screen, used while connecting to a server, downloading
 * terrain and loading singleplayer worlds: the logo fades in over a spinning
 * gradient ring, with the status (and progress, when known) underneath.
 *
 * One fade per loading sequence: screens drawn within a second of each other
 * (Connecting -> Loading terrain) continue it instead of fading in again.
 */
public final class NovoLoading {
    private static final ResourceLocation LOGO = new ResourceLocation(NovoMC.MODID, "textures/gui/logo.png");
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int PURPLE = 0xFFB45BFF;
    private static long shownAt;
    private static long lastDraw;

    private NovoLoading() {}

    /**
     * Draws the whole screen. `progress` is 0..1, or below 0 when unknown.
     */
    public static void draw(int width, int height, String title, String status, float progress) {
        long now = System.currentTimeMillis();
        if (now - lastDraw > 1000) {
            shownAt = now;
        }
        lastDraw = now;
        float fade = Math.min(1f, (now - shownAt) / 600f);
        float ease = 1 - (1 - fade) * (1 - fade);

        gradient(width, height, 0xFF0C1019, 0xFF15102A);
        int cx = width / 2;
        int cy = height / 2 - 10;

        // Spinner ring around the logo.
        float angle = (now % 1200L) / 1200f * 360f;
        ring(cx, cy, 30, 3f, angle, ease);

        // Logo, rising slightly as it fades in.
        int alpha = Math.max(4, Math.round(ease * 255));
        float size = 40;
        Draw.icon(LOGO, cx - size / 2, cy - size / 2 + (1 - ease) * 6, size, Draw.withAlpha(0xFFFFFF, alpha));

        if (title != null && !title.isEmpty()) {
            Draw.text(title, cx - Draw.width(title) / 2f, cy + 42, Draw.withAlpha(0xE8ECF4, alpha));
        }
        if (status != null && !status.isEmpty()) {
            String s = Draw.trim(status, width - 40);
            Draw.text(s, cx - Draw.width(s) / 2f, cy + 54, Draw.withAlpha(0x8A93A6, alpha));
        }
        if (progress >= 0) {
            float w = Math.min(160, width - 60);
            float x = cx - w / 2, y = cy + 70;
            Draw.round(x, y, x + w, y + 3, 1.5f, Draw.withAlpha(0x2A3142, alpha));
            Draw.round(x, y, x + Math.max(3, w * Math.min(1, progress)), y + 3, 1.5f, Draw.withAlpha(ACCENT, alpha));
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void gradient(int w, int h, int top, int bottom) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(w, 0, 0).color((top >> 16) & 255, (top >> 8) & 255, top & 255, 255).endVertex();
        wr.pos(0, 0, 0).color((top >> 16) & 255, (top >> 8) & 255, top & 255, 255).endVertex();
        wr.pos(0, h, 0).color((bottom >> 16) & 255, (bottom >> 8) & 255, bottom & 255, 255).endVertex();
        wr.pos(w, h, 0).color((bottom >> 16) & 255, (bottom >> 8) & 255, bottom & 255, 255).endVertex();
        t.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    /** A 270-degree arc that fades in along its length (blue to purple), starting at `angle`. */
    private static void ring(float cx, float cy, float r, float thickness, float angle, float alpha) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int segments = 60;
        for (int i = 0; i <= segments; i++) {
            float f = i / (float) segments;
            double a = Math.toRadians(angle + f * 270);
            int c = Draw.blend(ACCENT, PURPLE, f);
            int al = Math.round(f * alpha * 255);
            double cos = Math.cos(a), sin = Math.sin(a);
            wr.pos(cx + cos * (r - thickness), cy + sin * (r - thickness), 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, al).endVertex();
            wr.pos(cx + cos * (r + thickness), cy + sin * (r + thickness), 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, al).endVertex();
        }
        t.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
