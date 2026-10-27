/*
 * Ported from Sodium 0.5 (CaffeineMC/sodium, mc1.20.1-0.5.11),
 * me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex.
 * Copyright (C) JellySquid and Sodium contributors. Modified for NovoMC: it
 * re-encodes the meshes vanilla 1.8.9 builds instead of Sodium's own mesher.
 * Licensed under the GNU LGPL v3; see LICENSE-SODIUM.txt in this package.
 */
package com.novomc.client.sodium;

import java.nio.ByteBuffer;

/**
 * Sodium's 20-byte terrain vertex. Vanilla's BLOCK format is 28 bytes
 * (3 position floats, RGBA bytes, 2 UV floats, 2 lightmap shorts):
 *
 * <pre>
 *  0  ushort x, y, z   chunk-local position, (pos + 8) * 2048
 *  6  ubyte  material  (unused, 0)
 *  7  ubyte  section   index of the chunk in its region (LocalSectionIndex)
 *  8  ubyte  r, g, b, a
 * 12  ushort u, v      atlas UV * 32768
 * 16  ushort block, sky light
 * </pre>
 */
public final class CompactChunkVertex {
    public static final int STRIDE = 20;
    public static final int VANILLA_STRIDE = 28;

    private static final int POSITION_MAX_VALUE = 65536;
    private static final int TEXTURE_MAX_VALUE = 32768;
    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;
    private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;

    /** Shader constants, as in Sodium's ChunkVertexType. */
    public static final float POSITION_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;
    public static final float POSITION_OFFSET = -MODEL_ORIGIN;
    public static final float TEXTURE_SCALE = 1.0f / TEXTURE_MAX_VALUE;

    private CompactChunkVertex() {}

    /** Re-encodes {@code count} vanilla vertices from {@code src} (absolute, from 0) into {@code dst}. */
    public static void encode(ByteBuffer src, int count, int sectionIndex, ByteBuffer dst) {
        byte section = (byte) (sectionIndex & 0xFF);
        for (int i = 0; i < count; i++) {
            int s = i * VANILLA_STRIDE;
            int d = i * STRIDE;
            dst.putShort(d, encodePosition(src.getFloat(s)));
            dst.putShort(d + 2, encodePosition(src.getFloat(s + 4)));
            dst.putShort(d + 4, encodePosition(src.getFloat(s + 8)));
            dst.put(d + 6, (byte) 0);
            dst.put(d + 7, section);
            dst.putInt(d + 8, src.getInt(s + 12));
            dst.putShort(d + 12, encodeTexture(src.getFloat(s + 16)));
            dst.putShort(d + 14, encodeTexture(src.getFloat(s + 20)));
            dst.putInt(d + 16, src.getInt(s + 24));
        }
        dst.position(0).limit(count * STRIDE);
    }

    private static short encodePosition(float v) {
        return (short) ((MODEL_ORIGIN + v) * MODEL_SCALE_INV);
    }

    private static short encodeTexture(float value) {
        return (short) (Math.round(value * TEXTURE_MAX_VALUE) & 0xFFFF);
    }
}
