package com.novomc.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * The 3D wings drawn behind a player's torso.
 *
 * Geometry is a small tree of untextured boxes: three hinges per wing (root,
 * mid, tip) so the outer feathers follow the shoulder with a delay. Each wing
 * is about 20 boxes, drawn with one {@code glBegin} in flat colors: cheap
 * enough to draw only for the players who are on screen, and no texture atlas
 * or animation ticks are involved. Flap time comes from a monotonic clock, so
 * the motion looks the same at any frame rate.
 */
final class WingModel {
    private static final Map<String, WingModel> CACHE = new HashMap<String, WingModel>();

    private final Part left;
    private final Part right;
    private final float rate;
    private final float amplitude;

    private WingModel(float[][] colors, float rate, float amplitude) {
        this.rate = rate;
        this.amplitude = amplitude;
        this.left = wing(1.0F, colors);
        this.right = wing(-1.0F, colors);
    }

    /** The model for a wing id from the relay, or null if we don't ship it. */
    static WingModel get(String id) {
        WingModel model = CACHE.get(id);
        if (model != null) {
            return model;
        }
        float[][] colors;
        float rate;
        float amplitude;
        if ("angel_wings".equals(id)) {
            colors = Palette.ANGEL;
            rate = 1.45F;
            amplitude = 0.62F;
        } else if ("demon_wings".equals(id)) {
            colors = Palette.DEMON;
            rate = 1.65F;
            amplitude = 0.72F;
        } else if ("crystal_wings".equals(id)) {
            colors = Palette.CRYSTAL;
            rate = 1.35F;
            amplitude = 0.58F;
        } else {
            return null;
        }
        model = new WingModel(colors, rate, amplitude);
        CACHE.put(id, model);
        return model;
    }

    /**
     * @param seconds elapsed time from {@code System.nanoTime()}, not ticks
     * @param scale   1/16 to draw in the player model's units
     */
    void render(float seconds, float scale) {
        float phase = seconds * rate * (float) (Math.PI * 2.0);
        float flap = (float) Math.sin(phase) * amplitude;
        float sweep = (float) Math.sin(phase - 0.65F) * 0.08F;
        pose(left, flap, sweep);
        pose(right, -flap, -sweep);

        NovoCosmetics.beginFlat();
        draw(left, scale);
        draw(right, scale);
        NovoCosmetics.endFlat();
    }

    private static void pose(Part root, float flap, float sweep) {
        root.rotZ = flap;
        root.rotY = sweep;
        if (root.children.isEmpty()) {
            return;
        }
        Part mid = root.children.get(0);
        mid.rotZ = flap * 0.52F;
        mid.rotY = sweep * 0.5F;
        if (!mid.children.isEmpty()) {
            mid.children.get(0).rotZ = flap * 0.25F;
        }
    }

    private static void draw(Part part, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(part.px * scale, part.py * scale, part.pz * scale);
        if (part.rotZ != 0.0F) {
            GlStateManager.rotate(part.rotZ * 57.29578F, 0.0F, 0.0F, 1.0F);
        }
        if (part.rotY != 0.0F) {
            GlStateManager.rotate(part.rotY * 57.29578F, 0.0F, 1.0F, 0.0F);
        }
        // Matrices must not change inside glBegin/glEnd, so each hinge draws
        // its own boxes and then recurses.
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < part.boxes.size(); i++) {
            box(part.boxes.get(i), scale);
        }
        GL11.glEnd();
        for (int i = 0; i < part.children.size(); i++) {
            draw(part.children.get(i), scale);
        }
        GlStateManager.popMatrix();
    }

    /** One cuboid as six shaded quads; the wings are the only thing that uses it. */
    private static void box(Box b, float s) {
        float x0 = b.x * s;
        float y0 = b.y * s;
        float z0 = b.z * s;
        float x1 = (b.x + b.w) * s;
        float y1 = (b.y + b.h) * s;
        float z1 = (b.z + b.d) * s;
        // back, front, right, left, bottom, top (local +y points down, +z back)
        quad(b.color, 1.0F, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(b.color, 0.82F, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(b.color, 0.9F, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        quad(b.color, 0.9F, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(b.color, 0.7F, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(b.color, 1.05F, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void quad(float[] color, float shade, float... corners) {
        // GlStateManager keeps its colour cache in step; raw glColor would leave it stale.
        GlStateManager.color(color[0] * shade, color[1] * shade, color[2] * shade, 1.0F);
        for (int i = 0; i < corners.length; i += 3) {
            GL11.glVertex3f(corners[i], corners[i + 1], corners[i + 2]);
        }
    }

    /**
     * Left wing authored with positive X; the right one is mirrored. The root
     * sits at the top of the back, just behind a resting cape (z = 3).
     */
    private static Part wing(float side, float[][] colors) {
        Part root = new Part(side * 2.0F, 2.0F, 3.0F);
        add(root, side, 0.0F, -2.0F, 0.0F, 3.0F, 8.0F, 2.0F, colors[0]);
        add(root, side, 2.0F, -1.0F, 0.0F, 3.0F, 9.0F, 2.0F, colors[0]);
        add(root, side, 4.0F, 0.5F, 0.0F, 3.0F, 8.0F, 2.0F, colors[1]);
        add(root, side, 6.0F, 1.5F, 0.0F, 3.0F, 7.0F, 2.0F, colors[1]);

        Part mid = new Part(side * 5.0F, 2.0F, 0.0F);
        add(mid, side, 0.0F, -1.0F, 0.0F, 4.0F, 8.0F, 1.5F, colors[1]);
        add(mid, side, 2.0F, 1.0F, 0.0F, 4.0F, 7.0F, 1.5F, colors[2]);

        Part tip = new Part(side * 5.0F, 2.0F, 0.0F);
        add(tip, side, 0.0F, -1.0F, 0.0F, 4.0F, 6.0F, 1.5F, colors[2]);

        mid.children.add(tip);
        root.children.add(mid);
        return root;
    }

    private static void add(Part part, float side, float x, float y, float z, float w, float h, float d, float[] color) {
        Box b = new Box();
        b.x = side > 0.0F ? x : -x - w;
        b.y = y;
        b.z = z;
        b.w = w;
        b.h = h;
        b.d = d;
        b.color = color;
        part.boxes.add(b);
    }

    private static final class Part {
        final float px;
        final float py;
        final float pz;
        final List<Box> boxes = new ArrayList<Box>();
        final List<Part> children = new ArrayList<Part>();
        float rotZ;
        float rotY;

        Part(float px, float py, float pz) {
            this.px = px;
            this.py = py;
            this.pz = pz;
        }
    }

    private static final class Box {
        float x;
        float y;
        float z;
        float w;
        float h;
        float d;
        float[] color;
    }

    /** The pack's suggested colors, in model RGB. */
    private static final class Palette {
        static final float[][] ANGEL = { { 0.96F, 0.94F, 0.84F }, { 0.98F, 0.87F, 0.6F }, { 0.96F, 0.75F, 0.22F } };
        static final float[][] DEMON = { { 0.16F, 0.14F, 0.2F }, { 0.45F, 0.09F, 0.13F }, { 0.78F, 0.1F, 0.18F } };
        static final float[][] CRYSTAL = { { 0.42F, 0.86F, 1.0F }, { 0.12F, 0.47F, 0.75F }, { 0.67F, 0.8F, 0.9F } };
    }
}
