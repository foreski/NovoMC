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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * A fantasy hat mesh (`assets/novomc/models/hats/<id>.obj`, materials from
 * `materials.mtl`) baked into flat-shaded triangles the first time it is worn.
 *
 * The meshes are real OBJ geometry, so they don't fit Minecraft's box UV
 * pipeline; drawing them as untextured colored triangles keeps the material
 * colors from the MTL and needs no texture atlas. Each model is centered on
 * the head and scaled so its widest side matches the 8-unit head.
 */
final class HatMesh {
    private static final Set<String> KNOWN = new HashSet<String>(Arrays.asList(
            "wizard_hat", "demon_horns", "angel_halo", "royal_crown", "crystal_antlers"));
    private static final Map<String, HatMesh> CACHE = new HashMap<String, HatMesh>();
    private static Map<String, float[]> materials;

    private final float[] positions;
    private final float[] colors;
    private final float[] shades;
    private final float fit;

    private HatMesh(float[] positions, float[] colors, float[] shades, float fit) {
        this.positions = positions;
        this.colors = colors;
        this.shades = shades;
        this.fit = fit;
    }

    /** The mesh for a hat id from the relay, or null when it can't be loaded. */
    static HatMesh get(String id) {
        if (id == null || !KNOWN.contains(id)) {
            return null;
        }
        HatMesh mesh = CACHE.get(id);
        if (mesh == null) {
            mesh = load(id);
            if (mesh != null) {
                CACHE.put(id, mesh);
            }
        }
        return mesh;
    }

    /** Scale that makes the widest part of the mesh about as wide as the head. */
    float fit() {
        return fit;
    }

    void render() {
        NovoCosmetics.beginFlat();
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int t = 0; t < shades.length; t++) {
            float shade = shades[t];
            // GlStateManager keeps its colour cache in step; raw glColor would leave it stale.
            GlStateManager.color(colors[t * 3] * shade, colors[t * 3 + 1] * shade, colors[t * 3 + 2] * shade, 1.0F);
            for (int v = 0; v < 9; v += 3) {
                GL11.glVertex3f(positions[t * 9 + v], positions[t * 9 + v + 1], positions[t * 9 + v + 2]);
            }
        }
        GL11.glEnd();
        NovoCosmetics.endFlat();
    }

    private static HatMesh load(String id) {
        try {
            List<float[]> vertices = new ArrayList<float[]>();
            List<float[]> triangles = new ArrayList<float[]>(); // 9 coords + 3 rgb
            float[] color = { 0.8F, 0.8F, 0.8F };
            BufferedReader reader = open("models/hats/" + id + ".obj");
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("v ")) {
                        String[] p = line.split("\\s+");
                        if (p.length >= 4) {
                            vertices.add(new float[] { Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]) });
                        }
                    } else if (line.startsWith("usemtl ")) {
                        float[] found = materials().get(line.substring(7).trim());
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
                        // Fan-triangulate (the meshes use triangles and quads).
                        for (int i = 1; i + 1 < face.length; i++) {
                            triangle(vertices, triangles, face[0], face[i], face[i + 1], color);
                        }
                    }
                }
            } finally {
                reader.close();
            }
            if (triangles.isEmpty()) {
                return null;
            }
            return bake(triangles);
        } catch (Exception e) {
            System.err.println("[NovoMC] Could not load hat " + id + ": " + e);
            return null;
        }
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

    /** Centers the mesh on the head, scales it to the head, and bakes flat shading. */
    private static HatMesh bake(List<float[]> triangles) {
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < triangles.size(); i++) {
            float[] t = triangles.get(i);
            for (int v = 0; v < 9; v += 3) {
                minX = Math.min(minX, t[v]);
                maxX = Math.max(maxX, t[v]);
                minZ = Math.min(minZ, t[v + 2]);
                maxZ = Math.max(maxZ, t[v + 2]);
            }
        }
        float width = maxX - minX;
        float depth = maxZ - minZ;
        float widest = Math.max(width, depth);
        float fit = widest > 0.0001F ? 10.0F / widest : 1.0F;
        fit = Math.max(0.3F, Math.min(12.0F, fit));
        float cx = (minX + maxX) / 2.0F;
        float cz = (minZ + maxZ) / 2.0F;

        float[] positions = new float[triangles.size() * 9];
        float[] colors = new float[triangles.size() * 3];
        float[] shades = new float[triangles.size()];
        for (int i = 0; i < triangles.size(); i++) {
            float[] t = triangles.get(i);
            System.arraycopy(t, 0, positions, i * 9, 9);
            positions[i * 9] -= cx;
            positions[i * 9 + 3] -= cx;
            positions[i * 9 + 6] -= cx;
            positions[i * 9 + 2] -= cz;
            positions[i * 9 + 5] -= cz;
            positions[i * 9 + 8] -= cz;
            System.arraycopy(t, 9, colors, i * 3, 3);

            float ax = t[3] - t[0], ay = t[4] - t[1], az = t[5] - t[2];
            float bx = t[6] - t[0], by = t[7] - t[1], bz = t[8] - t[2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float up = length > 0.0F ? ny / length : 0.0F;
            shades[i] = Math.max(0.55F, Math.min(1.0F, 0.68F + 0.3F * Math.max(0.0F, up) - 0.1F * Math.max(0.0F, -up)));
        }
        return new HatMesh(positions, colors, shades, fit);
    }

    private static Map<String, float[]> materials() throws IOException {
        if (materials != null) {
            return materials;
        }
        Map<String, float[]> out = new HashMap<String, float[]>();
        BufferedReader reader = open("models/hats/materials.mtl");
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
        materials = out;
        return out;
    }

    private static BufferedReader open(String path) throws IOException {
        IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("novomc", path));
        return new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
    }
}
