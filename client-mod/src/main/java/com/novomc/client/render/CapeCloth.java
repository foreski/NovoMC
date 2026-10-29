package com.novomc.client.render;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.novomc.client.modules.NovoCapesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The player's cape as a piece of cloth instead of a flat slab.
 *
 * One cape is a grid of points pinned along the top edge, hung from the neck.
 * Each frame the free points are integrated with Verlet (gravity, the gusts of
 * a light breeze, the air pushed at them by the player's own movement) and then
 * pulled back to their neighbour distances, so the sheet keeps its size and
 * shape while it moves. Collision against the player's back stops it from
 * sinking into the body.
 *
 * Everything is simulated in the model space vanilla draws capes in: one unit is
 * 1/16 block, x runs across the shoulders, y hangs down, and -z points away from
 * the player's back. The simulation is frame-rate independent and stepped at a
 * fixed rate, so a fast machine and a slow one see the same cloth.
 *
 * One cloth per player, keyed by UUID, and each is stepped once per rendered
 * frame. A cape nobody is drawing costs nothing.
 */
public final class CapeCloth {
    /** Vertices across the shoulders and down the cape. */
    static final int COLS = 9;
    static final int ROWS = 17;
    /** Size of the sheet in model units: the same 10x16 slab vanilla draws. */
    private static final float WIDTH = 10.0F;
    private static final float LENGTH = 16.0F;
    /** Rest distance from the player's back, so the cloth hangs clear of the body. */
    private static final float REST_Z = -1.0F;
    /** How far the cloth may swing towards the player before it is stopped. */
    private static final float BODY_Z = -0.65F;
    /** Unit is 1/16 block, so this is about 0.9 block/s² of pull. */
    private static final float GRAVITY = 24.0F;
    private static final float DAMPING = 0.972F;
    /** Fixed step, in seconds: every machine runs the same cloth. */
    private static final float STEP = 1.0F / 120.0F;
    /** Most steps one frame may run: a hitch should not spend a second in cloth. */
    private static final int MAX_STEPS = 8;
    private static final int ITERATIONS = 4;

    /** Live cloths by player. */
    private static final Map<UUID, CapeCloth> CLOTHS = new ConcurrentHashMap<UUID, CapeCloth>();
    private static final long START = System.nanoTime();
    /** A cape not rendered for this long is dropped, along with its points. */
    private static final long FORGET_AFTER_MS = 60000L;

    private final float[] x = new float[COLS * ROWS];
    private final float[] y = new float[COLS * ROWS];
    private final float[] z = new float[COLS * ROWS];
    private final float[] px = new float[COLS * ROWS];
    private final float[] py = new float[COLS * ROWS];
    private final float[] pz = new float[COLS * ROWS];
    /** Where this step integrates to, before the constraints are applied. */
    private final float[] nx = new float[COLS * ROWS];
    private final float[] ny = new float[COLS * ROWS];
    private final float[] nz = new float[COLS * ROWS];
    /** Constraint pairs and their rest lengths, built once. */
    private final int[] a;
    private final int[] b;
    private final float[] rest;
    private final float[] stiffness;
    private final float phase;
    /** Time left over from the last frame, in seconds, not yet stepped. */
    private float carry;
    private long lastStep;
    private long lastSeen;

    private CapeCloth(UUID id) {
        // A per-player phase keeps two capes side by side from waving in lockstep.
        phase = (id.hashCode() & 0xFFFF) / 65535.0F * 6.2831855F;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int i = row * COLS + col;
                x[i] = px[i] = -WIDTH / 2.0F + WIDTH * col / (COLS - 1);
                y[i] = py[i] = LENGTH * row / (ROWS - 1);
                z[i] = pz[i] = REST_Z;
            }
        }
        int horizontal = (COLS - 1) * ROWS;
        int vertical = COLS * (ROWS - 1);
        int shear = 2 * (COLS - 1) * (ROWS - 1);
        int bend = (COLS - 2) * ROWS + COLS * (ROWS - 2);
        a = new int[horizontal + vertical + shear + bend];
        b = new int[a.length];
        rest = new float[a.length];
        stiffness = new float[a.length];
        int n = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS - 1; col++) {
                n = link(n, row, col, row, col + 1, 1.0F);
            }
        }
        for (int row = 0; row < ROWS - 1; row++) {
            for (int col = 0; col < COLS; col++) {
                n = link(n, row, col, row + 1, col, 1.0F);
            }
        }
        for (int row = 0; row < ROWS - 1; row++) {
            for (int col = 0; col < COLS - 1; col++) {
                n = link(n, row, col, row + 1, col + 1, 0.5F);
                n = link(n, row, col + 1, row + 1, col, 0.5F);
            }
        }
        // Two-apart links resist folding, which is what makes the sheet read as
        // cloth rather than as netting.
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS - 2; col++) {
                n = link(n, row, col, row, col + 2, 0.12F);
            }
        }
        for (int row = 0; row < ROWS - 2; row++) {
            for (int col = 0; col < COLS; col++) {
                n = link(n, row, col, row + 2, col, 0.12F);
            }
        }
    }

    /** Adds a link between two grid points, with the length it has at rest. */
    private int link(int n, int rowA, int colA, int rowB, int colB, float strength) {
        int i = rowA * COLS + colA;
        int j = rowB * COLS + colB;
        a[n] = i;
        b[n] = j;
        rest[n] = (float) Math.sqrt(
                (x[i] - x[j]) * (x[i] - x[j])
                + (y[i] - y[j]) * (y[i] - y[j])
                + (z[i] - z[j]) * (z[i] - z[j]));
        stiffness[n] = strength;
        return n + 1;
    }

    /** The cloth for a player, made the first time they are drawn. */
    static CapeCloth of(UUID id) {
        CapeCloth cloth = CLOTHS.get(id);
        if (cloth == null) {
            cloth = new CapeCloth(id);
            CLOTHS.put(id, cloth);
        }
        return cloth;
    }

    /** Drops the cloth of players nobody has drawn in a while. */
    static void forgetUnseen() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, CapeCloth>> it = CLOTHS.entrySet().iterator(); it.hasNext();) {
            if (now - it.next().getValue().lastSeen > FORGET_AFTER_MS) {
                it.remove();
            }
        }
    }

    /**
     * Whether the cape layer should hand over to the cloth: physics on, and a
     * cape that would actually be drawn.
     */
    public static boolean takesOver(AbstractClientPlayer player) {
        return NovoCapesModule.physics()
                && player.hasPlayerInfo()
                && !player.isInvisible()
                && player.isWearing(EnumPlayerModelParts.CAPE)
                && player.getLocationCape() != null;
    }

    /**
     * Draws a player's cape as cloth, in place of vanilla's cape layer.
     *
     * The matrix work is the cape layer's own: the lag of the shoulders as the
     * body turns, the tilt with speed, the walk sway and the extra 25 degrees
     * while sneaking. Only the slab at the end is replaced, so a cloth cape sits
     * exactly where the old one did.
     */
    public static void render(AbstractClientPlayer player, float partialTicks, float scale) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 0.125F);
        double lagX = player.prevChasingPosX + (player.chasingPosX - player.prevChasingPosX) * partialTicks
                - (player.prevPosX + (player.posX - player.prevPosX) * partialTicks);
        double lagY = player.prevChasingPosY + (player.chasingPosY - player.prevChasingPosY) * partialTicks
                - (player.prevPosY + (player.posY - player.prevPosY) * partialTicks);
        double lagZ = player.prevChasingPosZ + (player.chasingPosZ - player.prevChasingPosZ) * partialTicks
                - (player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks);
        float yaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
        double sin = MathHelper.sin(yaw * (float) Math.PI / 180.0F);
        double cos = -MathHelper.cos(yaw * (float) Math.PI / 180.0F);
        float tilt = (float) (lagY * 10.0D);
        tilt = MathHelper.clamp_float(tilt, -6.0F, 32.0F);
        float swing = (float) ((lagX * sin + lagZ * cos) * 100.0D);
        float roll = (float) ((lagX * cos - lagZ * sin) * 100.0D);
        if (swing < 0.0F) {
            swing = 0.0F;
        }
        float sway = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
        tilt += MathHelper.sin((player.prevDistanceWalkedModified
                + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks) * 6.0F) * 32.0F * sway;
        if (player.isSneaking()) {
            tilt += 25.0F;
        }
        GlStateManager.rotate(6.0F + swing / 2.0F + tilt, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(roll / 2.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-roll / 2.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(scale, scale, scale);

        CapeCloth cloth = of(player.getUniqueID());
        cloth.tick(player);
        cloth.draw(player.getLocationCape());
        GlStateManager.popMatrix();
    }

    /** Steps the cloth for this frame, however long it has been since the last one. */
    private void tick(AbstractClientPlayer player) {
        float now = (System.nanoTime() - START) / 1.0E9F;
        if (lastStep == 0L) {
            lastStep = System.nanoTime();
        }
        float dt = (System.nanoTime() - lastStep) / 1.0E9F;
        lastStep = System.nanoTime();
        lastSeen = System.currentTimeMillis();
        // A window that was hidden for a while comes back with a huge delta:
        // step once rather than simulating the gap.
        if (dt < 0.0F || dt > 0.25F) {
            dt = STEP;
        }

        // The player's own air: moving forward streams the cape back, falling
        // lifts it. Velocities are per tick (1/20 s) in blocks.
        float yaw = (float) Math.toRadians(player.renderYawOffset);
        double vx = (player.posX - player.prevPosX) * 20.0D;
        double vy = (player.posY - player.prevPosY) * 20.0D;
        double vz = (player.posZ - player.prevPosZ) * 20.0D;
        float forward = (float) (-vx * Math.sin(yaw) + vz * Math.cos(yaw));
        float side = (float) (vx * Math.cos(yaw) + vz * Math.sin(yaw));
        float lift = Math.max(0.0F, (float) -vy);

        // The cloth is stepped in fixed slices, however long the frame was.
        // Stepping once per frame with the frame's own delta made a fast client
        // (2000+ fps) solve the constraints thousands of times a second while
        // barely applying any force: the sheet rolled itself into a tube and
        // spun around the neck. Carrying the remainder keeps every machine on
        // the same 120 steps a second.
        carry += dt;
        int steps = 0;
        while (carry >= STEP && steps < MAX_STEPS) {
            step(STEP, now, forward, side, lift);
            carry -= STEP;
            steps++;
        }
        if (carry >= STEP) {
            carry = 0.0F;
        }
        forgetUnseen();
    }

    /** One physics step: integrate, then hold the sheet together. */
    private void step(float dt, float time, float forward, float side, float lift) {
        float dt2 = dt * dt;
        for (int row = 1; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int i = row * COLS + col;
                // Gusts breathe in and out, and a faster ripple runs down the
                // cloth, strongest at the hem where there is nothing holding it.
                float down = row / (float) (ROWS - 1);
                float wave = MathHelper.sin(time * 2.1F + down * 2.4F + phase) * 0.5F
                        + MathHelper.sin(time * 1.27F + down * 4.1F + phase * 1.7F) * 0.5F;
                float ax = wave * (1.4F + 5.0F * down) + side * 3.0F * down;
                float ay = GRAVITY * (0.35F + 0.65F * down);
                // Running and falling both push the hem out behind the player.
                float az = -Math.abs(wave) * (0.8F + 2.6F * down)
                        - Math.abs(forward) * 3.2F * down
                        - lift * 2.0F * down;
                nx[i] = x[i] + (x[i] - px[i]) * DAMPING + ax * dt2;
                ny[i] = y[i] + (y[i] - py[i]) * DAMPING + ay * dt2;
                nz[i] = z[i] + (z[i] - pz[i]) * DAMPING + az * dt2;
            }
        }
        for (int i = 0; i < x.length; i++) {
            px[i] = x[i];
            py[i] = y[i];
            pz[i] = z[i];
            x[i] = nx[i];
            y[i] = ny[i];
            z[i] = nz[i];
        }
        for (int pass = 0; pass < ITERATIONS; pass++) {
            for (int c = 0; c < a.length; c++) {
                relax(a[c], b[c], rest[c], stiffness[c]);
            }
        }
        holdShape();
    }

    /** Pulls two points to the distance they should be apart. */
    private void relax(int i, int j, float restLength, float strength) {
        float dx = x[j] - x[i];
        float dy = y[j] - y[i];
        float dz = z[j] - z[i];
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.0001F) {
            return;
        }
        boolean pinnedA = i < COLS;
        boolean pinnedB = j < COLS;
        if (pinnedA && pinnedB) {
            return;
        }
        float move = (d - restLength) / d * strength * ((pinnedA || pinnedB) ? 1.0F : 0.5F);
        if (!pinnedA) {
            x[i] += dx * move;
            y[i] += dy * move;
            z[i] += dz * move;
        }
        if (!pinnedB) {
            x[j] -= dx * move;
            y[j] -= dy * move;
            z[j] -= dz * move;
        }
    }

    /** Puts the collar back on the neck and keeps the cloth out of the body. */
    private void holdShape() {
        for (int col = 0; col < COLS; col++) {
            x[col] = -WIDTH / 2.0F + WIDTH * col / (COLS - 1);
            y[col] = 0.0F;
            z[col] = REST_Z;
        }
        for (int i = COLS; i < x.length; i++) {
            if (z[i] > BODY_Z) {
                z[i] = BODY_Z;
            }
            if (z[i] < REST_Z - LENGTH) {
                z[i] = REST_Z - LENGTH;
            }
            // The hem may flare a little as it swings, but not into a flag.
            if (x[i] < -WIDTH / 2.0F - 2.0F) {
                x[i] = -WIDTH / 2.0F - 2.0F;
            } else if (x[i] > WIDTH / 2.0F + 2.0F) {
                x[i] = WIDTH / 2.0F + 2.0F;
            }
            if (y[i] > LENGTH) {
                y[i] = LENGTH;
            }
        }
    }

    /** Draws the sheet: one quad per cell, shaded by how far it faces the light. */
    private void draw(ResourceLocation cape) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(cape);
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for (int row = 0; row < ROWS - 1; row++) {
            for (int col = 0; col < COLS - 1; col++) {
                int p0 = row * COLS + col;
                int p1 = p0 + 1;
                int p2 = p0 + COLS + 1;
                int p3 = p0 + COLS;
                // Face normal from two edge vectors of the cell.
                float e1x = x[p1] - x[p0], e1y = y[p1] - y[p0], e1z = z[p1] - z[p0];
                float e2x = x[p3] - x[p0], e2y = y[p3] - y[p0], e2z = z[p3] - z[p0];
                float nxv = e1y * e2z - e1z * e2y;
                float nyv = e1z * e2x - e1x * e2z;
                float nzv = e1x * e2y - e1y * e2x;
                float len = (float) Math.sqrt(nxv * nxv + nyv * nyv + nzv * nzv);
                if (len > 0.0001F) {
                    nxv /= len;
                    nyv /= len;
                    nzv /= len;
                }
                // The same kind of baked shading vanilla's boxes use: faces
                // turned away from the viewer are darker.
                float shade = 0.62F + 0.38F * Math.abs(nzv);
                float u0 = (1.0F + WIDTH * col / (COLS - 1)) / 64.0F;
                float u1 = (1.0F + WIDTH * (col + 1) / (COLS - 1)) / 64.0F;
                float v0 = (1.0F + LENGTH * row / (ROWS - 1)) / 32.0F;
                float v1 = (1.0F + LENGTH * (row + 1) / (ROWS - 1)) / 32.0F;
                vertex(wr, p0, u0, v0, shade, nxv, nyv, nzv);
                vertex(wr, p1, u1, v0, shade, nxv, nyv, nzv);
                vertex(wr, p2, u1, v1, shade, nxv, nyv, nzv);
                vertex(wr, p3, u0, v1, shade, nxv, nyv, nzv);
            }
        }
        Tessellator.getInstance().draw();
        GlStateManager.enableCull();
    }

    private void vertex(WorldRenderer wr, int i, float u, float v, float shade, float nxv, float nyv, float nzv) {
        wr.pos(x[i], y[i], z[i]).tex(u, v).color(shade, shade, shade, 1.0F).normal(nxv, nyv, nzv).endVertex();
    }
}
