package com.novomc.client.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

/**
 * Shading for the OBJ cosmetics, shared by {@link WingModel} and {@link HatMesh}.
 *
 * The meshes ship without normals, so baking the face normal left a curved
 * membrane of two thousand triangles looking like flat facets, and one
 * directional light gave every panel the same brightness. This averages the
 * faces around each vertex instead, so a curved surface renders as a smooth
 * gradient, while anything sharper than the crease angle keeps its hard edge
 * (which is what a strut or a crown ridge needs).
 *
 * The lighting is a three-light rig baked into vertex colours: a warm key from
 * above and left, a cool fill from below and behind so the shadow side still
 * has shape, and the material's own {@code Ks}/{@code Ns} gloss. All of it is
 * static, so it costs nothing per frame — the wings stay two display lists.
 */
final class MeshShading {
    /**
     * Warm key light from above and slightly left, in OBJ space (y up). The
     * sideways part is deliberately small: the two wings are mirrored meshes,
     * so a light with much of a left or right bias renders them at noticeably
     * different brightnesses and a symmetric cosmetic starts looking broken.
     */
    private static final float[] KEY = normalize(-0.14F, 0.80F, 0.58F);
    /** Cool fill from below and behind, also near the midline: keeps the shadowed side from going flat black. */
    private static final float[] FILL = normalize(0.42F, -0.30F, -0.85F);
    /** Where the camera sits behind the player: the gloss lobe and the thin-edge rim are built around it. */
    private static final float[] VIEW = normalize(0.0F, 0.20F, 1.0F);
    private static final float[] HALF = normalize(
            KEY[0] + VIEW[0], KEY[1] + VIEW[1], KEY[2] + VIEW[2]);

    /**
     * The rig is deliberately contrasty. A wing membrane is close to planar, so
     * every normal points the same way and a soft rig renders it as one flat
     * field of colour; the range from ambient to key is what gives whatever
     * shape the mesh does have something to show against.
     */
    private static final float AMBIENT = 0.26F;
    private static final float KEY_GAIN = 0.62F;
    private static final float FILL_GAIN = 0.14F;
    /** Membrane is thin, so its edges catch light: brightest where the surface turns away from the camera. */
    private static final float RIM_GAIN = 0.12F;
    /** Per-channel tint of the key and the fill: warm versus cool is most of what makes a flat colour read as volume. */
    private static final float KEY_R = 1.05F, KEY_G = 1.00F, KEY_B = 0.95F;
    private static final float FILL_R = 0.80F, FILL_G = 0.90F, FILL_B = 1.05F;
    private static final float SPEC_GAIN = 0.85F;
    /** The occluded end of the ramp, for surfaces tucked against the body. */
    private static final float AO_FLOOR = 0.62F;

    private MeshShading() {}

    /**
     * Unit normals per triangle corner, averaged over the faces sharing that
     * position but only across edges gentler than {@code creaseDegrees}.
     *
     * @param corners 9 floats per triangle: three positions, xyz
     * @return {@code [triangle * 3 + corner]} for each corner
     */
    static float[][] cornerNormals(float[] corners, float creaseDegrees) {
        int triangles = corners.length / 9;
        // Cross products, not normalized, so bigger faces pull the average further.
        float[] face = new float[triangles * 3];
        float[] unit = new float[triangles * 3];
        Map<Position, List<Integer>> incident = new HashMap<Position, List<Integer>>();
        for (int t = 0; t < triangles; t++) {
            int o = t * 9;
            float ax = corners[o + 3] - corners[o];
            float ay = corners[o + 4] - corners[o + 1];
            float az = corners[o + 5] - corners[o + 2];
            float bx = corners[o + 6] - corners[o];
            float by = corners[o + 7] - corners[o + 1];
            float bz = corners[o + 8] - corners[o + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            face[t * 3] = nx;
            face[t * 3 + 1] = ny;
            face[t * 3 + 2] = nz;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0E-9F) {
                unit[t * 3] = nx / length;
                unit[t * 3 + 1] = ny / length;
                unit[t * 3 + 2] = nz / length;
            } else {
                // Degenerate triangle: point up rather than producing NaN shading.
                unit[t * 3 + 1] = 1.0F;
            }
            for (int k = 0; k < 3; k++) {
                int v = o + k * 3;
                Position key = new Position(corners[v], corners[v + 1], corners[v + 2]);
                List<Integer> faces = incident.get(key);
                if (faces == null) {
                    faces = new ArrayList<Integer>();
                    incident.put(key, faces);
                }
                faces.add(Integer.valueOf(t));
            }
        }

        float crease = (float) Math.cos(Math.toRadians(creaseDegrees));
        float[][] out = new float[triangles * 3][];
        for (int t = 0; t < triangles; t++) {
            for (int k = 0; k < 3; k++) {
                int v = t * 9 + k * 3;
                List<Integer> faces = incident.get(new Position(corners[v], corners[v + 1], corners[v + 2]));
                float sx = 0.0F, sy = 0.0F, sz = 0.0F;
                if (faces != null) {
                    for (int i = 0; i < faces.size(); i++) {
                        int f = faces.get(i).intValue();
                        float dot = unit[f * 3] * unit[t * 3]
                                + unit[f * 3 + 1] * unit[t * 3 + 1]
                                + unit[f * 3 + 2] * unit[t * 3 + 2];
                        if (dot >= crease) {
                            sx += face[f * 3];
                            sy += face[f * 3 + 1];
                            sz += face[f * 3 + 2];
                        }
                    }
                }
                float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (length < 1.0E-9F) {
                    out[t * 3 + k] = new float[] { unit[t * 3], unit[t * 3 + 1], unit[t * 3 + 2] };
                } else {
                    out[t * 3 + k] = new float[] { sx / length, sy / length, sz / length };
                }
            }
        }
        return out;
    }

    /** Darkens the end of a surface tucked against the body, to fake the contact shadow. */
    static float occlude(float t) {
        float clamped = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        return AO_FLOOR + (1.0F - AO_FLOOR) * clamped;
    }

    /**
     * A contact shadow around the structural bones, per triangle corner.
     *
     * This is the term that actually draws detail on a membrane: the surface
     * between two ribs is flat, so lighting it by normal changes nothing, but
     * darkening it where it meets a bone makes each rib read as something the
     * skin is stretched over instead of a line drawn on top.
     *
     * @param corners    9 floats per triangle
     * @param ribCentres 3 floats per structural triangle, its centroid
     * @param reach      how far the shadow spreads, in OBJ units
     * @param strength   how much it darkens at the bone, 0..1
     */
    static float[] boneContact(float[] corners, float[] ribCentres, float reach, float strength) {
        int vertices = corners.length / 3;
        int ribs = ribCentres.length / 3;
        float[] out = new float[vertices];
        float reachSquared = reach * reach;
        for (int v = 0; v < vertices; v++) {
            float x = corners[v * 3];
            float y = corners[v * 3 + 1];
            float z = corners[v * 3 + 2];
            float nearest = Float.MAX_VALUE;
            for (int r = 0; r < ribs; r++) {
                float dx = ribCentres[r * 3] - x;
                float dy = ribCentres[r * 3 + 1] - y;
                float dz = ribCentres[r * 3 + 2] - z;
                float distance = dx * dx + dy * dy + dz * dz;
                if (distance < nearest) {
                    nearest = distance;
                }
                if (nearest == 0.0F) {
                    break;
                }
            }
            if (ribs == 0 || nearest >= reachSquared) {
                out[v] = 1.0F;
            } else {
                float t = 1.0F - (float) Math.sqrt(nearest) / reach;
                out[v] = 1.0F - strength * t * t;
            }
        }
        return out;
    }

    /**
     * Lights one corner into {@code out[0..2]}.
     *
     * @param normal   unit normal from {@link #cornerNormals}
     * @param material 7 floats per triangle: {@code Kd} rgb, {@code Ks} rgb, {@code Ns}
     * @param offset   index of this triangle's material
     * @param ao       from {@link #occlude}
     */
    static void shade(float[] normal, float[] material, int offset, float ao, float[] out) {
        float diffR = AMBIENT + KEY_GAIN * Math.max(0.0F, dot(normal, KEY)) * KEY_R
                + FILL_GAIN * Math.max(0.0F, dot(normal, FILL)) * FILL_R;
        float diffG = AMBIENT + KEY_GAIN * Math.max(0.0F, dot(normal, KEY)) * KEY_G
                + FILL_GAIN * Math.max(0.0F, dot(normal, FILL)) * FILL_G;
        float diffB = AMBIENT + KEY_GAIN * Math.max(0.0F, dot(normal, KEY)) * KEY_B
                + FILL_GAIN * Math.max(0.0F, dot(normal, FILL)) * FILL_B;

        float gloss = (material[offset + 3] + material[offset + 4] + material[offset + 5]) / 3.0F;
        float spec = 0.0F;
        if (gloss > 0.0F) {
            float n = material[offset + 6] > 1.0F ? material[offset + 6] : 1.0F;
            spec = gloss * SPEC_GAIN * (float) Math.pow(Math.max(0.0F, dot(normal, HALF)), n) * ao;
        }
        // Grazing angles glow a little, which is what stops a membrane reading as a solid slab.
        float edge = 1.0F - Math.abs(dot(normal, VIEW));
        float rim = RIM_GAIN * edge * edge * edge * ao;

        out[0] = clamp(material[offset] * (diffR * ao + rim) + rim * 0.4F + spec);
        out[1] = clamp(material[offset + 1] * (diffG * ao + rim) + rim * 0.4F + spec);
        out[2] = clamp(material[offset + 2] * (diffB * ao + rim) + rim * 0.4F + spec);
    }

    /**
     * The {@code Kd}, {@code Ks} and {@code Ns} of every material in an MTL, as
     * 7 floats each. Absent values fall back to a matte finish.
     */
    static Map<String, float[]> materials(String path) throws IOException {
        Map<String, float[]> out = new HashMap<String, float[]>();
        IResource resource = Minecraft.getMinecraft().getResourceManager()
                .getResource(new ResourceLocation("novomc", path));
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        try {
            String name = null;
            float[] current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("newmtl ")) {
                    name = line.substring(7).trim();
                    current = new float[] { 0.8F, 0.8F, 0.8F, 0.15F, 0.15F, 0.15F, 24.0F };
                    out.put(name, current);
                } else if (current != null && line.startsWith("Kd ")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4) {
                        current[0] = Float.parseFloat(p[1]);
                        current[1] = Float.parseFloat(p[2]);
                        current[2] = Float.parseFloat(p[3]);
                    }
                } else if (current != null && line.startsWith("Ks ")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4) {
                        current[3] = Float.parseFloat(p[1]);
                        current[4] = Float.parseFloat(p[2]);
                        current[5] = Float.parseFloat(p[3]);
                    }
                } else if (current != null && line.startsWith("Ns ")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 2) {
                        current[6] = Float.parseFloat(p[1]);
                    }
                }
            }
        } finally {
            reader.close();
        }
        return out;
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float[] normalize(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6F) {
            return new float[] { 0.0F, 1.0F, 0.0F };
        }
        return new float[] { x / length, y / length, z / length };
    }

    /** Vertices are welded by position at a tenth of a millimetre, so shared corners smooth together. */
    private static final class Position {
        private final int x;
        private final int y;
        private final int z;

        Position(float x, float y, float z) {
            this.x = Math.round(x * 10000.0F);
            this.y = Math.round(y * 10000.0F);
            this.z = Math.round(z * 10000.0F);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Position)) {
                return false;
            }
            Position p = (Position) other;
            return x == p.x && y == p.y && z == p.z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}
