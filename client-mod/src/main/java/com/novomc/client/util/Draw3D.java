package com.novomc.client.util;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** Helpers for drawing lines and boxes in the world (camera-relative). */
public final class Draw3D {
    private Draw3D() {}

    /** Interpolated camera position {x, y, z}. */
    public static double[] camera(Entity view, float partialTicks) {
        return new double[] {
            view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks,
            view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks,
            view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks
        };
    }

    /** Untextured, blended, no depth writes; call {@link #end()} after drawing. */
    public static void begin(float lineWidth) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GL11.glLineWidth(lineWidth);
    }

    public static void end() {
        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    public static void color(int rgb, float alpha) {
        GlStateManager.color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    /** Solid faces of a box in the current color. */
    public static void filledBox(AxisAlignedBB b) {
        Tessellator t = Tessellator.getInstance();
        WorldRenderer w = t.getWorldRenderer();
        w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        // bottom, top
        w.pos(b.minX, b.minY, b.minZ).endVertex(); w.pos(b.maxX, b.minY, b.minZ).endVertex(); w.pos(b.maxX, b.minY, b.maxZ).endVertex(); w.pos(b.minX, b.minY, b.maxZ).endVertex();
        w.pos(b.minX, b.maxY, b.minZ).endVertex(); w.pos(b.minX, b.maxY, b.maxZ).endVertex(); w.pos(b.maxX, b.maxY, b.maxZ).endVertex(); w.pos(b.maxX, b.maxY, b.minZ).endVertex();
        // north, south
        w.pos(b.minX, b.minY, b.minZ).endVertex(); w.pos(b.minX, b.maxY, b.minZ).endVertex(); w.pos(b.maxX, b.maxY, b.minZ).endVertex(); w.pos(b.maxX, b.minY, b.minZ).endVertex();
        w.pos(b.minX, b.minY, b.maxZ).endVertex(); w.pos(b.maxX, b.minY, b.maxZ).endVertex(); w.pos(b.maxX, b.maxY, b.maxZ).endVertex(); w.pos(b.minX, b.maxY, b.maxZ).endVertex();
        // west, east
        w.pos(b.minX, b.minY, b.minZ).endVertex(); w.pos(b.minX, b.minY, b.maxZ).endVertex(); w.pos(b.minX, b.maxY, b.maxZ).endVertex(); w.pos(b.minX, b.maxY, b.minZ).endVertex();
        w.pos(b.maxX, b.minY, b.minZ).endVertex(); w.pos(b.maxX, b.maxY, b.minZ).endVertex(); w.pos(b.maxX, b.maxY, b.maxZ).endVertex(); w.pos(b.maxX, b.minY, b.maxZ).endVertex();
        t.draw();
    }

    /** Starts a batch of colored lines; add pairs with {@link #line}, then {@link #drawLines()}. */
    public static WorldRenderer lines() {
        WorldRenderer w = Tessellator.getInstance().getWorldRenderer();
        w.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        return w;
    }

    public static void line(WorldRenderer w, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        w.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        w.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

    public static void drawLines() {
        Tessellator.getInstance().draw();
    }
}
