/*
 * Based on Sodium 0.5 (CaffeineMC/sodium, mc1.20.1-0.5.11),
 * me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion and LocalSectionIndex.
 * Copyright (C) JellySquid and Sodium contributors. Modified for NovoMC.
 * Licensed under the GNU LGPL v3; see LICENSE-SODIUM.txt in this package.
 */
package com.novomc.client.sodium;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * An 8x4x8 block of 16^3 chunk sections whose meshes share one arena buffer
 * and one vertex array, so a whole region draws with a single multi-draw.
 */
public final class RenderRegion {
    public static final int WIDTH = 8;
    public static final int HEIGHT = 4;
    public static final int LENGTH = 8;
    public static final int WIDTH_SH = 3;
    public static final int HEIGHT_SH = 2;
    public static final int LENGTH_SH = 3;
    public static final int SECTIONS = WIDTH * HEIGHT * LENGTH;

    /** Room for roughly two dense chunk layers before the first resize. */
    private static final int INITIAL_VERTICES = 16 * 1024;

    public final int originX;
    public final int originY;
    public final int originZ;
    public final long key;

    private final GlBufferArena arena = new GlBufferArena(INITIAL_VERTICES, CompactChunkVertex.STRIDE);
    private int vao;
    private int vaoBuffer;

    // Draw batch for the layer being rendered, filled in render-list order.
    IntBuffer firsts = BufferUtils.createIntBuffer(SECTIONS);
    IntBuffer counts = BufferUtils.createIntBuffer(SECTIONS);
    int batchFrame = -1;

    RenderRegion(int rx, int ry, int rz) {
        originX = rx << (WIDTH_SH + 4);
        originY = ry << (HEIGHT_SH + 4);
        originZ = rz << (LENGTH_SH + 4);
        key = key(rx, ry, rz);
    }

    /** Region key for a section origin in block coordinates. */
    public static long keyForBlock(int x, int y, int z) {
        return key(x >> (WIDTH_SH + 4), y >> (HEIGHT_SH + 4), z >> (LENGTH_SH + 4));
    }

    static long key(int rx, int ry, int rz) {
        return ((long) (rx & 0x3FFFFF) << 42) | ((long) (ry & 0xFFFFF) << 22) | (rz & 0x3FFFFF);
    }

    /** Sodium's LocalSectionIndex packing: x in bits 5-7, y in bits 0-1, z in bits 2-4. */
    public static int localIndex(int blockX, int blockY, int blockZ) {
        int x = (blockX >> 4) & (WIDTH - 1);
        int y = (blockY >> 4) & (HEIGHT - 1);
        int z = (blockZ >> 4) & (LENGTH - 1);
        return x << 5 | y | z << 2;
    }

    GlBufferSegment upload(ByteBuffer data) {
        return arena.upload(data);
    }

    boolean isEmpty() {
        return arena.isEmpty();
    }

    int allocatedBytes() {
        return arena.getDeviceAllocatedMemory();
    }

    void addDraw(int first, int count) {
        if (!firsts.hasRemaining()) {
            firsts = grow(firsts);
            counts = grow(counts);
        }
        firsts.put(first);
        counts.put(count);
    }

    private static IntBuffer grow(IntBuffer old) {
        IntBuffer bigger = BufferUtils.createIntBuffer(old.capacity() * 2);
        old.flip();
        bigger.put(old);
        return bigger;
    }

    void resetBatch() {
        firsts.clear();
        counts.clear();
    }

    /** Binds the vertex array, re-pointing it if the arena moved to a bigger buffer. */
    void bind() {
        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
        }
        GL30.glBindVertexArray(vao);
        int buffer = arena.getBufferObject();
        if (vaoBuffer != buffer) {
            vaoBuffer = buffer;
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            int stride = CompactChunkVertex.STRIDE;
            GL20.glEnableVertexAttribArray(ChunkShader.ATTR_POS_ID);
            GL30.glVertexAttribIPointer(ChunkShader.ATTR_POS_ID, 4, GL11.GL_UNSIGNED_SHORT, stride, 0);
            GL20.glEnableVertexAttribArray(ChunkShader.ATTR_COLOR);
            GL20.glVertexAttribPointer(ChunkShader.ATTR_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, stride, 8);
            GL20.glEnableVertexAttribArray(ChunkShader.ATTR_TEX);
            GL20.glVertexAttribPointer(ChunkShader.ATTR_TEX, 2, GL11.GL_UNSIGNED_SHORT, false, stride, 12);
            GL20.glEnableVertexAttribArray(ChunkShader.ATTR_LIGHT);
            GL30.glVertexAttribIPointer(ChunkShader.ATTR_LIGHT, 2, GL11.GL_UNSIGNED_SHORT, stride, 16);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    void delete() {
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        arena.delete();
    }
}
