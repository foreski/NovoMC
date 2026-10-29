package com.novomc.client.render;

import java.io.IOException;
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
 * A fantasy hat mesh (`assets/novomc/models/hats/<id>.obj`, materials from
 * `materials.mtl`) baked into smoothly shaded triangles the first time it is
 * worn.
 *
 * The meshes are real OBJ geometry, so they don't fit Minecraft's box UV
 * pipeline; drawing them as untextured colored triangles keeps the material
 * colors from the MTL and needs no texture atlas. Each model is centered on
 * the head and scaled so its widest side matches the 8-unit head.
 *
 * Normals and lighting are baked once by {@link MeshShading}: the crown of a
 * hat and the curve of an antler are smooth surfaces, and shading them per
 * face is what made them look carved out of blocks. The bottom of the mesh is
 * pushed into shadow, which is the contact shadow under the brim.
 */
final class HatMesh {
    private static final Set<String> KNOWN = new HashSet<String>(Arrays.asList(
            "wizard_hat", "demon_horns", "angel_halo", "royal_crown", "crystal_antlers"));
    private static final Map<String, HatMesh> CACHE = new HashMap<String, HatMesh>();
    private static Map<String, float[]> materials;

    /** Smoothed across anything gentler than this; a brim or a band stays a hard edge. */
    private static final float CREASE_DEGREES = 40.0F;

    private final float[] positions;
    private final float[] colors;
    private final float fit;

    private HatMesh(float[] positions, float[] colors, float fit) {
        this.positions = positions;
        this.colors = colors;
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
        for (int v = 0; v < positions.length / 3; v++) {
            // GlStateManager keeps its colour cache in step; raw glColor would leave it stale.
            GlStateManager.color(colors[v * 3], colors[v * 3 + 1], colors[v * 3 + 2], 1.0F);
            GL11.glVertex3f(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]);
        }
        GL11.glEnd();
        NovoCosmetics.endFlat();
    }

    private static HatMesh load(String id) {
        try {
            Map<String, float[]> palette = materials();
            List<float[]> vertices = new ArrayList<float[]>();
            List<float[]> triangles = new ArrayList<float[]>(); // 9 coords + 7 material floats
            float[] material = fallback(palette);
            IResource resource = Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("novomc", "models/hats/" + id + ".obj"));
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
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
                        float[] found = palette.get(line.substring(7).trim());
                        if (found != null) {
                            material = found;
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
                            triangle(vertices, triangles, face[0], face[i], face[i + 1], material);
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

    private static float[] fallback(Map<String, float[]> palette) {
        float[] any = palette.isEmpty() ? null : palette.values().iterator().next();
        return any != null ? any : new float[] { 0.8F, 0.8F, 0.8F, 0.15F, 0.15F, 0.15F, 24.0F };
    }

    private static void triangle(List<float[]> vertices, List<float[]> out, int a, int b, int c, float[] material) {
        if (a < 0 || b < 0 || c < 0 || a >= vertices.size() || b >= vertices.size() || c >= vertices.size()) {
            return;
        }
        float[] va = vertices.get(a);
        float[] vb = vertices.get(b);
        float[] vc = vertices.get(c);
        out.add(new float[] {
            va[0], va[1], va[2], vb[0], vb[1], vb[2], vc[0], vc[1], vc[2],
            material[0], material[1], material[2], material[3], material[4], material[5], material[6],
        });
    }

    /**
     * Centers the mesh on the head, scales it to the head, and bakes the
     * per-corner normals and lighting.
     */
    private static HatMesh bake(List<float[]> triangles) {
        int count = triangles.size();
        float[] corners = new float[count * 9];
        float[] palette = new float[count * 7];
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float[] t = triangles.get(i);
            System.arraycopy(t, 0, corners, i * 9, 9);
            System.arraycopy(t, 9, palette, i * 7, 7);
            for (int v = 0; v < 9; v += 3) {
                minX = Math.min(minX, t[v]);
                maxX = Math.max(maxX, t[v]);
                minY = Math.min(minY, t[v + 1]);
                maxY = Math.max(maxY, t[v + 1]);
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
        float height = Math.max(0.0001F, maxY - minY);

        float[][] normals = MeshShading.cornerNormals(corners, CREASE_DEGREES);

        float[] positions = new float[count * 9];
        float[] colors = new float[count * 9];
        float[] shaded = new float[3];
        for (int t = 0; t < count; t++) {
            for (int k = 0; k < 3; k++) {
                int o = t * 9 + k * 3;
                MeshShading.shade(normals[t * 3 + k], palette, t * 7,
                        MeshShading.occlude((corners[o + 1] - minY) / height), shaded);
                colors[o] = shaded[0];
                colors[o + 1] = shaded[1];
                colors[o + 2] = shaded[2];
                positions[o] = corners[o] - cx;
                positions[o + 1] = corners[o + 1];
                positions[o + 2] = corners[o + 2] - cz;
            }
        }
        return new HatMesh(positions, colors, fit);
    }

    private static Map<String, float[]> materials() throws IOException {
        if (materials == null) {
            materials = MeshShading.materials("models/hats/materials.mtl");
        }
        return materials;
    }
}
