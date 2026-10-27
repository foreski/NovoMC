/*
 * NovoMC's port of Sodium's chunk renderer to Minecraft 1.8.9. Draw path based on
 * Sodium 0.5 (CaffeineMC/sodium, mc1.20.1-0.5.11), DefaultChunkRenderer and
 * RenderRegionManager. Copyright (C) JellySquid and Sodium contributors.
 * Modified for NovoMC. Licensed under the GNU LGPL v3; see LICENSE-SODIUM.txt.
 */
package com.novomc.client.sodium;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Replaces vanilla's per-chunk VBO drawing. Vanilla 1.8.9 binds a buffer, sets
 * four vertex pointers, pushes a matrix and issues a draw for every chunk and
 * layer. Here chunk meshes are re-encoded into Sodium's compact 20-byte vertex
 * and packed into per-region arena buffers; each region then draws a whole
 * layer with one vertex-array bind and one glMultiDrawArrays, positioned by a
 * shader instead of the matrix stack.
 *
 * <p>Vanilla still builds the meshes and decides visibility. All calls happen
 * on the client thread.
 */
public final class SodiumRenderer {
    private static final Logger LOG = LogManager.getLogger("NovoMC Sodium");

    private static boolean enabled;
    private static Boolean supported;
    private static boolean failed;

    private static ChunkShader shader;
    private static final Map<Long, RenderRegion> REGIONS = new HashMap<>();
    private static final Map<VertexBuffer, Slot> SLOTS = new IdentityHashMap<>();
    private static final List<RenderRegion> BATCH = new ArrayList<>();
    private static ByteBuffer scratch = BufferUtils.createByteBuffer(64 * 1024);
    private static final FloatBuffer FOG_COLOR = BufferUtils.createFloatBuffer(16);
    private static int frame;

    /** A chunk layer's mesh inside its region's arena. */
    private static final class Slot {
        final RenderRegion region;
        final GlBufferSegment segment;

        Slot(RenderRegion region, GlBufferSegment segment) {
            this.region = region;
            this.segment = segment;
        }
    }

    private SodiumRenderer() {}

    /**
     * Whether chunk uploads and drawing go through this renderer. It must stay the
     * same between a chunk's upload and its draw, so every change rebuilds chunks.
     */
    public static boolean isActive() {
        return enabled && !failed && OpenGlHelper.useVbo() && isSupported();
    }

    /** OpenGL 3.3 (GLSL 330, integer attributes, vertex arrays, buffer copies). macOS legacy contexts stop at 2.1. */
    public static boolean isSupported() {
        if (supported == null) {
            supported = GLContext.getCapabilities().OpenGL33;
            if (!supported) {
                LOG.info("OpenGL 3.3 is not available; using vanilla chunk rendering");
            }
        }
        return supported;
    }

    public static void setEnabled(boolean on) {
        if (enabled != on) {
            enabled = on;
            reloadChunks();
        }
    }

    private static void reloadChunks() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    /** Takes over {@code ChunkRenderDispatcher.uploadChunk} on the client thread. Returns false to let vanilla upload. */
    public static boolean upload(EnumWorldBlockLayer layer, WorldRenderer worldRenderer, RenderChunk chunk) {
        // equals, not ==: translucent re-sorts restore a copy of the format.
        if (!DefaultVertexFormats.BLOCK.equals(worldRenderer.getVertexFormat())) {
            fail("unexpected chunk vertex format " + worldRenderer.getVertexFormat());
            return false;
        }
        VertexBuffer key = chunk.getVertexBufferByLayer(layer.ordinal());
        free(key);
        int count = worldRenderer.getVertexCount();
        if (count == 0) {
            return true;
        }
        try {
            BlockPos pos = chunk.getPosition();
            long regionKey = RenderRegion.keyForBlock(pos.getX(), pos.getY(), pos.getZ());
            RenderRegion region = REGIONS.get(regionKey);
            if (region == null) {
                region = new RenderRegion(pos.getX() >> 7, pos.getY() >> 6, pos.getZ() >> 7);
                REGIONS.put(regionKey, region);
            }
            int bytes = count * CompactChunkVertex.STRIDE;
            if (scratch.capacity() < bytes) {
                scratch = BufferUtils.createByteBuffer(Integer.highestOneBit(bytes) << 1);
            }
            ByteBuffer src = worldRenderer.getByteBuffer().duplicate().order(ByteOrder.nativeOrder());
            scratch.clear();
            CompactChunkVertex.encode(src, count,
                    RenderRegion.localIndex(pos.getX(), pos.getY(), pos.getZ()), scratch);
            SLOTS.put(key, new Slot(region, region.upload(scratch)));
        } catch (RuntimeException e) {
            fail(e.toString());
            LOG.error("Chunk upload failed", e);
        }
        return true;
    }

    /** A chunk's vertex buffer was deleted: release its arena space. */
    public static void free(VertexBuffer key) {
        Slot slot = SLOTS.remove(key);
        if (slot != null) {
            slot.segment.delete();
            if (slot.region.isEmpty()) {
                REGIONS.remove(slot.region.key);
                slot.region.delete();
            }
        }
    }

    /** Draws one layer of the chunks vanilla collected this frame. */
    public static void render(EnumWorldBlockLayer layer, List<RenderChunk> chunks, double camX, double camY, double camZ) {
        if (chunks.isEmpty()) {
            return;
        }
        if (shader == null) {
            try {
                shader = new ChunkShader();
            } catch (RuntimeException e) {
                fail(e.getMessage());
                return;
            }
        }
        frame++;
        int ordinal = layer.ordinal();
        for (RenderChunk chunk : chunks) {
            Slot slot = SLOTS.get(chunk.getVertexBufferByLayer(ordinal));
            if (slot == null) {
                continue;
            }
            RenderRegion region = slot.region;
            if (region.batchFrame != frame) {
                region.batchFrame = frame;
                region.resetBatch();
                BATCH.add(region);
            }
            region.addDraw(slot.segment.getOffset(), slot.segment.getLength());
        }
        if (BATCH.isEmpty()) {
            return;
        }

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(shader.program);
        shader.loadMatrix(shader.uProjection, GL11.GL_PROJECTION_MATRIX);
        shader.loadMatrix(shader.uModelView, GL11.GL_MODELVIEW_MATRIX);
        GL20.glUniform1i(shader.uBlockTex, OpenGlHelper.defaultTexUnit - GL13.GL_TEXTURE0);
        GL20.glUniform1i(shader.uLightTex, OpenGlHelper.lightmapTexUnit - GL13.GL_TEXTURE0);
        setupFog();

        for (RenderRegion region : BATCH) {
            region.firsts.flip();
            region.counts.flip();
            GL20.glUniform3f(shader.uRegionOffset,
                    (float) (region.originX - camX), (float) (region.originY - camY), (float) (region.originZ - camZ));
            region.bind();
            GL14.glMultiDrawArrays(GL11.GL_QUADS, region.firsts, region.counts);
        }
        BATCH.clear();

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL20.glUseProgram(previousProgram);
    }

    private static void setupFog() {
        if (!GL11.glIsEnabled(GL11.GL_FOG)) {
            GL20.glUniform1i(shader.uFogMode, 0);
            return;
        }
        int mode = GL11.glGetInteger(GL11.GL_FOG_MODE);
        GL20.glUniform1i(shader.uFogMode, mode == GL11.GL_EXP ? 2 : mode == GL11.GL_EXP2 ? 3 : 1);
        FOG_COLOR.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, FOG_COLOR);
        GL20.glUniform4f(shader.uFogColor, FOG_COLOR.get(0), FOG_COLOR.get(1), FOG_COLOR.get(2), FOG_COLOR.get(3));
        GL20.glUniform1f(shader.uFogStart, getFloat(GL11.GL_FOG_START));
        GL20.glUniform1f(shader.uFogEnd, getFloat(GL11.GL_FOG_END));
        GL20.glUniform1f(shader.uFogDensity, getFloat(GL11.GL_FOG_DENSITY));
    }

    private static float getFloat(int pname) {
        FOG_COLOR.clear();
        GL11.glGetFloat(pname, FOG_COLOR);
        return FOG_COLOR.get(0);
    }

    /** Video memory the arenas hold, for the debug screen. */
    public static String debugLine() {
        long bytes = 0;
        for (RenderRegion region : REGIONS.values()) {
            bytes += region.allocatedBytes();
        }
        return "Sodium: " + REGIONS.size() + " regions, " + SLOTS.size() + " meshes, " + (bytes >> 20) + " MB";
    }

    /** Something went wrong: switch to vanilla for the rest of the session and rebuild with it. */
    private static void fail(String reason) {
        if (!failed) {
            failed = true;
            LOG.error("Falling back to vanilla chunk rendering: {}", reason);
            Minecraft.getMinecraft().addScheduledTask(SodiumRenderer::reloadChunks);
        }
    }
}
