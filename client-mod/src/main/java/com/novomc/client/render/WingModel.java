package com.novomc.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * The 3D wings drawn behind a player's torso: the OBJ meshes in
 * `assets/novomc/models/wings/<id>.obj`, colored by the materials in
 * `<id>.mtl`.
 *
 * The meshes come in LeftWing and RightWing objects and are each baked into
 * two display lists: the section inboard of the elbow and the section outboard
 * of it, hinged at the elbow the way a real wing bends at the wrist. A worn
 * pair therefore costs four {@code glCallList} calls a frame instead of several
 * thousand vertices. Each side also pivots around the shoulder hinge the mesh
 * was authored around (x = +-0.13, y = 0.10, z = 0.05 in OBJ space).
 *
 * The motion is the one the 1.8.9 Wings Mod 1.2 uses (see
 * `docs/RE_WINGS_MOD.md`): the shoulder flaps, twists and sweeps out of phase
 * with each other, and the outer section trails behind the shoulder instead of
 * following it rigidly, which is what stops the wing looking like a cut-out
 * being rocked. Time comes from a monotonic clock, so the motion looks the
 * same at any frame rate.
 *
 * Shading is baked per vertex by {@link MeshShading}, so the membrane reads as
 * a gradient across its curve rather than as several thousand flat facets. The
 * light is fixed in mesh space, which means it turns with the player: at that
 * size nobody is reading it as a real light source, and the alternative is
 * relighting thousands of vertices per frame.
 */
final class WingModel {
    private static final Set<String> KNOWN = new HashSet<String>(Arrays.asList(
            "violet_wings", "verdant_wings", "monochrome_wings", "azure_wings",
            "ender_dragon_wings", "phantom_wings"));
    private static final Map<String, WingModel> CACHE = new HashMap<String, WingModel>();

    /**
     * OBJ units to player-model units. The mesh spans 2.94 units, so a pair
     * reaches about 1.1 blocks either side of the spine — big enough to be the
     * silhouette it is meant to be, without swallowing the player.
     */
    private static final float SCALE = 12.0F;
    /** The hinge's place on the torso, in model units (y points down, z points back). */
    private static final float BODY_Y = 1.2F;
    private static final float BODY_Z = 2.0F;
    /**
     * The shoulder hinge the meshes were authored around. The OBJ origin sits
     * between the two attachment points, so it is on the spine (x = 0), and the
     * wings hang symmetrically off it.
     */
    private static final float HINGE_Y = 0.10F;
    private static final float HINGE_Z = 0.05F;
    /** Wings rest swept back a little, the way they sit against a back. */
    private static final float REST_SWEEP = 0.16F;
    /** Where along the span the outer section hinges, as a fraction of the reach from the shoulder. */
    private static final float ELBOW_FRACTION = 0.48F;
    /**
     * How much geometry the two halves share either side of the elbow, in OBJ
     * units. Without it the fold opens a wedge you can see through; overlapping
     * a band means one half always covers the seam. Both halves share normals,
     * so the overlap shades identically and only reads as a crease.
     */
    private static final float SEAM_OVERLAP = 0.07F;
    private static final float RAD_TO_DEG = 57.29578F;
    /** Smoothed across anything gentler than this, which keeps the struts crisp and the membrane soft. */
    private static final float CREASE_DEGREES = 46.0F;
    /** Reach from the shoulder at which the contact shading has faded out entirely, in OBJ units. */
    private static final float AO_REACH = 1.3F;
    /** How far the membrane darkens around the ribs, and by how much at the bone itself. */
    private static final float BONE_REACH = 0.16F;
    private static final float BONE_STRENGTH = 0.42F;
    /**
     * Greyscale art, multiplied by each material's colour, so the texture
     * follows the palette in the `.mtl` and only the detail has to be painted.
     * One file per pair, drawn by `node scripts/make-wing-textures.mjs`.
     */
    private static ResourceLocation texture(String id) {
        return new ResourceLocation("novomc", "textures/wings/" + id + ".png");
    }
    /** How far the outer section lags the shoulder, in radians of phase. */
    private static final float ELBOW_LAG = 1.5F;
    /** How far the sweep lags the flap, so the wing follows through instead of moving as one piece. */
    private static final float SWEEP_LAG = 0.9F;

    private final Side left;
    private final Side right;
    private final float rate;
    private final float amplitude;
    private final ResourceLocation texture;

    private WingModel(Side left, Side right, float rate, float amplitude, ResourceLocation texture) {
        this.left = left;
        this.right = right;
        this.rate = rate;
        this.amplitude = amplitude;
        this.texture = texture;
    }

    /** One side of the pair: the two display lists and the elbow they hinge on. */
    private static final class Side {
        private final int root;
        private final int outer;
        /** The elbow, in baked model units relative to the shoulder hinge. */
        private final float pivotX;
        private final float pivotY;
        private final float pivotZ;

        private Side(int root, int outer, float pivotX, float pivotY, float pivotZ) {
            this.root = root;
            this.outer = outer;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
        }
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
        // The sweep trails the flap and the twist leads it, so the three axes never move as one rigid piece.
        float sweep = REST_SWEEP + (float) Math.sin(phase - SWEEP_LAG) * amplitude * 0.35F;
        float twist = -(float) Math.cos(phase) * amplitude * 0.28F;
        // Negative so the outer section is still catching up when the shoulder turns around.
        float elbow = -(float) Math.sin(phase - ELBOW_LAG) * amplitude * 0.55F;

        NovoCosmetics.beginFlat();
        // The membrane detail lives in this texture; the vertex colours above
        // tint it, so turning texturing back on is all it takes.
        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        textures.bindTexture(texture);
        GlStateManager.enableTexture2D();
        pose(left, 1.0F, flap, sweep, twist, elbow, scale);
        pose(right, -1.0F, flap, sweep, twist, elbow, scale);
        GlStateManager.disableTexture2D();
        NovoCosmetics.endFlat();
    }

    /**
     * Both sides are authored with the hinge at the origin, so one rotation at
     * the shoulder tilts the whole wing, and a second at the elbow bends what
     * is left of it. The sides mirror, hence the sign.
     */
    private static void pose(Side side, float sign, float flap, float sweep, float twist, float elbow, float scale) {
        if (!GL11.glIsList(side.root)) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(0.0F, BODY_Y, BODY_Z);
        GlStateManager.rotate(sign * twist * RAD_TO_DEG, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(sign * sweep * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(sign * flap * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
        GL11.glCallList(side.root);
        // Carried by the shoulder above, and bent from there.
        GlStateManager.translate(side.pivotX, side.pivotY, side.pivotZ);
        GlStateManager.rotate(sign * elbow * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
        GL11.glCallList(side.outer);
        GlStateManager.popMatrix();
    }

    private static WingModel load(String id) {
        try {
            Map<String, float[]> materials = MeshShading.materials("models/wings/" + id + ".mtl");
            List<float[]> vertices = new ArrayList<float[]>();
            List<float[]> left = new ArrayList<float[]>();
            List<float[]> right = new ArrayList<float[]>();
            List<float[]> side = left;
            List<Boolean> leftRibs = new ArrayList<Boolean>();
            List<Boolean> rightRibs = new ArrayList<Boolean>();
            List<Boolean> ribSide = leftRibs;
            boolean rib = false;
            float[] material = material(materials, null);
            IResource resource = Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("novomc", "models/wings/" + id + ".obj"));
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
                    } else if (line.startsWith("o ") || line.startsWith("g ")) {
                        boolean isRight = line.substring(2).trim().toLowerCase().contains("right");
                        side = isRight ? right : left;
                        ribSide = isRight ? rightRibs : leftRibs;
                    } else if (line.startsWith("usemtl ")) {
                        String name = line.substring(7).trim();
                        material = material(materials, name);
                        // "frame" is the structural bonework; the skin is stretched over it.
                        rib = "frame".equalsIgnoreCase(name);
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
                            triangle(vertices, side, ribSide, face[0], face[i], face[i + 1], material, rib);
                        }
                    }
                }
            } finally {
                reader.close();
            }
            if (left.isEmpty() || right.isEmpty()) {
                return null;
            }
            Side bakedLeft = bake(left, leftRibs, -1.0F);
            Side bakedRight = bake(right, rightRibs, 1.0F);
            if (bakedLeft == null || bakedRight == null) {
                return null;
            }
            float[] flap = flap(id);
            return new WingModel(bakedLeft, bakedRight, flap[0], flap[1], texture(id));
        } catch (Exception e) {
            System.err.println("[NovoMC] Could not load wings " + id + ": " + e);
            return null;
        }
    }

    /** How fast this pair beats: cycles per second and the swing in radians. */
    private static float[] flap(String id) {
        // All four sit near the reference's one cycle a second, with just enough spread to tell them apart.
        if ("verdant_wings".equals(id)) {
            return new float[] { 1.15F, 0.46F };
        }
        if ("monochrome_wings".equals(id)) {
            return new float[] { 0.95F, 0.38F };
        }
        if ("azure_wings".equals(id)) {
            return new float[] { 1.30F, 0.50F };
        }
        // A dragon beats slowly and heavily; a phantom twitches.
        if ("ender_dragon_wings".equals(id)) {
            return new float[] { 0.85F, 0.52F };
        }
        if ("phantom_wings".equals(id)) {
            return new float[] { 1.90F, 0.62F };
        }
        return new float[] { 1.05F, 0.42F };
    }

    private static float[] material(Map<String, float[]> materials, String name) {
        float[] found = name == null ? null : materials.get(name);
        if (found != null) {
            return found;
        }
        float[] fallback = materials.get("membrane");
        return fallback != null ? fallback : new float[] { 0.72F, 0.72F, 0.78F, 0.16F, 0.16F, 0.16F, 24.0F };
    }

    private static void triangle(List<float[]> vertices, List<float[]> out, List<Boolean> ribs,
            int a, int b, int c, float[] material, boolean rib) {
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
        ribs.add(Boolean.valueOf(rib));
    }

    /**
     * Bakes one side into the inboard and outboard lists. Normals are built
     * over the whole side before it is split, so the shading runs continuously
     * across the elbow instead of breaking at the join.
     */
    private static Side bake(List<float[]> triangles, List<Boolean> ribs, float sign) {
        int count = triangles.size();
        float[] corners = new float[count * 9];
        float[] materials = new float[count * 7];
        float extreme = sign < 0.0F ? Float.MAX_VALUE : -Float.MAX_VALUE;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float[] t = triangles.get(i);
            System.arraycopy(t, 0, corners, i * 9, 9);
            System.arraycopy(t, 9, materials, i * 7, 7);
            for (int v = 0; v < 9; v += 3) {
                float x = t[v];
                extreme = sign < 0.0F ? Math.min(extreme, x) : Math.max(extreme, x);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, t[v + 1]);
                maxY = Math.max(maxY, t[v + 1]);
            }
        }
        // The texture is projected flat across the side: the meshes ship no UVs,
        // and a membrane is close enough to planar for it to hold.
        float[] box = { minX, minY, Math.max(1.0E-4F, maxX - minX), Math.max(1.0E-4F, maxY - minY) };
        float[][] normals = MeshShading.cornerNormals(corners, CREASE_DEGREES);

        float[] ribCentres = new float[count * 3];
        int ribCount = 0;
        for (int t = 0; t < count; t++) {
            if (!ribs.get(t).booleanValue()) {
                continue;
            }
            int o = t * 9;
            ribCentres[ribCount * 3] = (corners[o] + corners[o + 3] + corners[o + 6]) / 3.0F;
            ribCentres[ribCount * 3 + 1] = (corners[o + 1] + corners[o + 4] + corners[o + 7]) / 3.0F;
            ribCentres[ribCount * 3 + 2] = (corners[o + 2] + corners[o + 5] + corners[o + 8]) / 3.0F;
            ribCount++;
        }
        float[] contact = null;
        if (ribCount > 0) {
            float[] centres = new float[ribCount * 3];
            System.arraycopy(ribCentres, 0, centres, 0, centres.length);
            contact = MeshShading.boneContact(corners, centres, BONE_REACH, BONE_STRENGTH);
        }

        // The elbow sits a little past halfway out, where the membrane is widest.
        float elbowX = extreme * ELBOW_FRACTION;
        // Its pivot is the middle of the seam the split cuts, so both halves meet there.
        float seamY = 0.0F;
        float seamZ = 0.0F;
        int seam = 0;
        for (int i = 0; i < corners.length; i += 3) {
            if (Math.abs(corners[i] - elbowX) < 0.12F) {
                seamY += corners[i + 1];
                seamZ += corners[i + 2];
                seam++;
            }
        }
        if (seam == 0) {
            seamY = HINGE_Y;
            seamZ = HINGE_Z;
        } else {
            seamY /= seam;
            seamZ /= seam;
        }

        boolean[] inboard = new boolean[count];
        boolean[] outboard = new boolean[count];
        for (int t = 0; t < count; t++) {
            float cx = (corners[t * 9] + corners[t * 9 + 3] + corners[t * 9 + 6]) / 3.0F;
            float past = sign * (cx - elbowX);
            inboard[t] = past > -SEAM_OVERLAP;
            outboard[t] = past < SEAM_OVERLAP;
        }

        float[] shoulder = { 0.0F, HINGE_Y, HINGE_Z };
        boolean mirror = sign < 0.0F;
        int rootList = compile(corners, materials, normals, contact, inboard, shoulder, shoulder, box, mirror);
        float[] elbow = { elbowX, seamY, seamZ };
        int outerList = compile(corners, materials, normals, contact, outboard, elbow, shoulder, box, mirror);
        if (rootList == 0 || outerList == 0) {
            return null;
        }
        return new Side(rootList, outerList,
                elbowX * SCALE,
                -(seamY - HINGE_Y) * SCALE,
                (seamZ - HINGE_Z) * SCALE);
    }

    /**
     * Flips the Y-up mesh into the model's Y-down space, subtracts the pivot so
     * that part turns about it, and bakes the triangles into a display list,
     * shading each corner from its shared normal and fading the shoulder into
     * shadow.
     */
    private static int compile(float[] corners, float[] materials, float[][] normals, float[] contact,
            boolean[] include, float[] pivot, float[] shoulder, float[] box, boolean mirror) {
        int list = GL11.glGenLists(1);
        if (list == 0) {
            return 0;
        }
        float[] shaded = new float[3];
        GL11.glNewList(list, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int t = 0; t < include.length; t++) {
            if (!include[t]) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                int o = t * 9 + k * 3;
                float ax = corners[o] - shoulder[0];
                float ay = corners[o + 1] - shoulder[1];
                float az = corners[o + 2] - shoulder[2];
                float reach = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                float shadow = MeshShading.occlude(reach / AO_REACH);
                if (contact != null) {
                    shadow *= contact[t * 3 + k];
                }
                MeshShading.shade(normals[t * 3 + k], materials, t * 7, shadow, shaded);
                // Mirrored so both sides read the art the same way round, with
                // u 0 at the hinge where the veins converge.
                float u = (corners[o] - box[0]) / box[2];
                if (mirror) {
                    u = 1.0F - u;
                }
                GL11.glTexCoord2f(u, 1.0F - (corners[o + 1] - box[1]) / box[3]);
                // GlStateManager keeps its colour cache in step; raw glColor would leave it stale.
                GlStateManager.color(shaded[0], shaded[1], shaded[2], 1.0F);
                GL11.glVertex3f((corners[o] - pivot[0]) * SCALE,
                        -(corners[o + 1] - pivot[1]) * SCALE,
                        (corners[o + 2] - pivot[2]) * SCALE);
            }
        }
        GL11.glEnd();
        GL11.glEndList();
        return list;
    }
}
