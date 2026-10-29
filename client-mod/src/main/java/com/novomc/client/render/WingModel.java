package com.novomc.client.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * The 3D wings drawn behind a player's torso: the OBJ meshes in
 * `assets/novomc/models/wings/<id>.obj`, colored by the materials in
 * `<id>.mtl`.
 *
 * The meshes come in LeftWing and RightWing objects and are baked once into a
 * display list per side, so a worn pair costs two {@code glCallList} calls a
 * frame instead of several thousand vertices. Each side pivots around the
 * shoulder hinge the mesh was authored around (x = +-0.13, y = 0.10, z = 0.05
 * in OBJ space), which is why the flap is a matrix rotation and needs no
 * per-vertex animation. Flap time comes from a monotonic clock, so the motion
 * looks the same at any frame rate.
 */
final class WingModel {
    private static final Set<String> KNOWN = new HashSet<String>(Arrays.asList(
            "violet_wings", "verdant_wings", "monochrome_wings", "azure_wings"));
    private static final Map<String, WingModel> CACHE = new HashMap<String, WingModel>();

    /** OBJ units to player-model units: the mesh spans 2.67 units, about 27 of the model's 16-per-block. */
    private static final float SCALE = 10.0F;
    /** The hinge's place on the torso, in model units (y points down, z points back). */
    private static final float BODY_Y = 1.2F;
    private static final float BODY_Z = 2.0F;
    /** The shoulder hinge the meshes were authored around. */
    private static final float HINGE_X = 0.13F;
    private static final float HINGE_Y = 0.10F;
    private static final float HINGE_Z = 0.05F;
    /** Wings rest swept back a little, the way they sit against a back. */
    private static final float REST_SWEEP = 0.16F;
    private static final float RAD_TO_DEG = 57.29578F;

    private final int leftList;
    private final int rightList;
    private final float rate;
    private final float amplitude;

    private WingModel(int leftList, int rightList, float rate, float amplitude) {
        this.leftList = leftList;
        this.rightList = rightList;
        this.rate = rate;
        this.amplitude = amplitude;
    }

    /** The model for a wing id from the relay, or null if we don't ship it. */
    static WingModel get(String id) {
        if (id == null || !KNOWN.contains(id)) {
            return null;
        }
        WingModel model = CACHE.get(id);
        if (model == null) {
            model = load(id);
            if (model != null) {
                CACHE.put(id, model);
            }
        }
        return model;
    }

    /**
     * @param seconds elapsed time from {@code System.nanoTime()}, not ticks
     * @param scale   1/16 to draw in the player model's units
     */
    void render(float seconds, float scale) {
        float phase = seconds * rate * (float) (Math.PI * 2.0);
        float flap = (float) Math.sin(phase) * amplitude;
        float sweep = (float) Math.sin(phase - 0.65F) * amplitude * 0.14F;

        NovoCosmetics.beginFlat();
        draw(leftList, 1.0F, flap, sweep, scale);
        draw(rightList, -1.0F, flap, sweep, scale);
        NovoCosmetics.endFlat();
    }

    /**
     * Both sides are authored with the hinge at the origin, so one rotation at
     * the shoulder tilts the whole wing. The sides mirror, hence the sign.
     */
    private static void draw(int list, float side, float flap, float sweep, float scale) {
        if (!GL11.glIsList(list)) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(0.0F, BODY_Y, BODY_Z);
        GlStateManager.rotate(side * flap * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(side * (sweep + REST_SWEEP) * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
        GL11.glCallList(list);
        GlStateManager.popMatrix();
    }

    private static WingModel load(String id) {
        try {
            Map<String, float[]> materials = materials(id);
            List<float[]> vertices = new ArrayList<float[]>();
            List<float[]> left = new ArrayList<float[]>();
            List<float[]> right = new ArrayList<float[]>();
            List<float[]> side = left;
            float[] color = { 0.72F, 0.72F, 0.78F };
            BufferedReader reader = open("models/wings/" + id + ".obj");
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("v ")) {
                        String[] p = line.split("\\s+");
                        if (p.length >= 4) {
                            vertices.add(new float[] { Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]) });
                        }
                    } else if (line.startsWith("o ") || line.startsWith("g ")) {
                        side = line.substring(2).trim().toLowerCase().contains("right") ? right : left;
                    } else if (line.startsWith("usemtl ")) {
                        float[] found = materials.get(line.substring(7).trim());
                        if (found != null) {
                            color = found;
                        }
                    } else if (line.startsWith("f ")) {
                        String[] p = line.split("\\s+");
                        int[] face = new int[p.length - 1];
                        for (int i = 0; i < face.length; i++) {
                            String token = p[i + 1];
                            int slash = token.indexOf('/');
                            int index = Integer.parseInt(slash >= 0 ? token.substring(0, slash) : token);
                            face[i] = index < 0 ? vertices.size() + index : index - 1;
                        }
                        // The meshes are quads; fan-triangulate so any face shape works.
                        for (int i = 1; i + 1 < face.length; i++) {
                            triangle(vertices, side, face[0], face[i], face[i + 1], color);
                        }
                    }
                }
            } finally {
                reader.close();
            }
            if (left.isEmpty() || right.isEmpty()) {
                return null;
            }
            float[] lift = { 0.18F, 0.42F, 0.89F };
            int leftList = compile(left, lift);
            int rightList = compile(right, lift);
            if (leftList == 0 || rightList == 0) {
                return null;
            }
            float[] flap = flap(id);
            return new WingModel(leftList, rightList, flap[0], flap[1]);
        } catch (Exception e) {
            System.err.println("[NovoMC] Could not load wings " + id + ": " + e);
            return null;
        }
    }

    /** How fast this pair beats: cycles per second and the swing in radians. */
    private static float[] flap(String id) {
        if ("verdant_wings".equals(id)) {
            return new float[] { 1.6F, 0.66F };
        }
        if ("monochrome_wings".equals(id)) {
            return new float[] { 1.3F, 0.54F };
        }
        if ("azure_wings".equals(id)) {
            return new float[] { 1.75F, 0.72F };
        }
        return new float[] { 1.45F, 0.6F };
    }

    private static void triangle(List<float[]> vertices, List<float[]> out, int a, int b, int c, float[] color) {
        if (a < 0 || b < 0 || c < 0 || a >= vertices.size() || b >= vertices.size() || c >= vertices.size()) {
            return;
        }
        float[] va = vertices.get(a);
        float[] vb = vertices.get(b);
        float[] vc = vertices.get(c);
        out.add(new float[] {
            va[0], va[1], va[2], vb[0], vb[1], vb[2], vc[0], vc[1], vc[2],
            color[0], color[1], color[2],
        });
    }

    /**
     * Moves the hinge to the origin, flips the Y-up mesh into the model's
     * Y-down space and bakes the triangles into a display list with flat
     * shading from one light behind the player.
     */
    private static int compile(List<float[]> triangles, float[] light) {
        int list = GL11.glGenLists(1);
        if (list == 0) {
            return 0;
        }
        GL11.glNewList(list, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < triangles.size(); i++) {
            float[] t = triangles.get(i);
            float ax = (t[0] - HINGE_X) * SCALE;
            float ay = -(t[1] - HINGE_Y) * SCALE;
            float az = (t[2] - HINGE_Z) * SCALE;
            float bx = (t[3] - HINGE_X) * SCALE;
            float by = -(t[4] - HINGE_Y) * SCALE;
            float bz = (t[5] - HINGE_Z) * SCALE;
            float cx = (t[6] - HINGE_X) * SCALE;
            float cy = -(t[7] - HINGE_Y) * SCALE;
            float cz = (t[8] - HINGE_Z) * SCALE;

            float edge1x = bx - ax, edge1y = by - ay, edge1z = bz - az;
            float edge2x = cx - ax, edge2y = cy - ay, edge2z = cz - az;
            float nx = edge1y * edge2z - edge1z * edge2y;
            float ny = edge1z * edge2x - edge1x * edge2z;
            float nz = edge1x * edge2y - edge1y * edge2x;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

            float shade = 0.62F;
            if (length > 0.0F) {
                // Light in OBJ space (y up); the model is two-sided, so use the absolute facing.
                float facing = Math.abs((nx * light[0] + ny * light[1] + nz * light[2]) / length);
                shade = 0.62F + 0.38F * facing;
            }
            // GlStateManager keeps its colour cache in step; raw glColor would leave it stale.
            GlStateManager.color(t[9] * shade, t[10] * shade, t[11] * shade, 1.0F);
            GL11.glVertex3f(ax, ay, az);
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f(cx, cy, cz);
        }
        GL11.glEnd();
        GL11.glEndList();
        return list;
    }

    /** The `Kd` of each material in this wing's MTL. */
    private static Map<String, float[]> materials(String id) throws IOException {
        Map<String, float[]> out = new HashMap<String, float[]>();
        BufferedReader reader = open("models/wings/" + id + ".mtl");
        try {
            String name = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("newmtl ")) {
                    name = line.substring(7).trim();
                } else if (name != null && line.startsWith("Kd ")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4) {
                        out.put(name, new float[] { Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]) });
                    }
                }
            }
        } finally {
            reader.close();
        }
        return out;
    }

    private static BufferedReader open(String path) throws IOException {
        IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("novomc", path));
        return new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
    }
}
