/*
 * Based on Sodium 0.5 (CaffeineMC/sodium, mc1.20.1-0.5.11),
 * me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface.
 * Copyright (C) JellySquid and Sodium contributors. Modified for NovoMC.
 * Licensed under the GNU LGPL v3; see LICENSE-SODIUM.txt in this package.
 */
package com.novomc.client.sodium;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** The terrain shader program and its uniforms. */
final class ChunkShader {
    static final int ATTR_POS_ID = 0;
    static final int ATTR_COLOR = 1;
    static final int ATTR_TEX = 2;
    static final int ATTR_LIGHT = 3;

    final int program;
    final int uProjection;
    final int uModelView;
    final int uRegionOffset;
    final int uBlockTex;
    final int uLightTex;
    final int uFogMode;
    final int uFogColor;
    final int uFogStart;
    final int uFogEnd;
    final int uFogDensity;

    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);

    ChunkShader() {
        String defines = "#define VERT_POS_SCALE " + CompactChunkVertex.POSITION_SCALE + "\n"
                + "#define VERT_POS_OFFSET " + CompactChunkVertex.POSITION_OFFSET + "\n"
                + "#define VERT_TEX_SCALE " + CompactChunkVertex.TEXTURE_SCALE + "\n";
        int vs = compile(GL20.GL_VERTEX_SHADER, withDefines(load("block_layer.vsh"), defines));
        int fs = compile(GL20.GL_FRAGMENT_SHADER, load("block_layer.fsh"));
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glBindAttribLocation(program, ATTR_POS_ID, "a_PosId");
        GL20.glBindAttribLocation(program, ATTR_COLOR, "a_Color");
        GL20.glBindAttribLocation(program, ATTR_TEX, "a_TexCoord");
        GL20.glBindAttribLocation(program, ATTR_LIGHT, "a_LightCoord");
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            String log = GL20.glGetProgramInfoLog(program, 4096);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("Sodium shader failed to link: " + log);
        }
        uProjection = GL20.glGetUniformLocation(program, "u_ProjectionMatrix");
        uModelView = GL20.glGetUniformLocation(program, "u_ModelViewMatrix");
        uRegionOffset = GL20.glGetUniformLocation(program, "u_RegionOffset");
        uBlockTex = GL20.glGetUniformLocation(program, "u_BlockTex");
        uLightTex = GL20.glGetUniformLocation(program, "u_LightTex");
        uFogMode = GL20.glGetUniformLocation(program, "u_FogMode");
        uFogColor = GL20.glGetUniformLocation(program, "u_FogColor");
        uFogStart = GL20.glGetUniformLocation(program, "u_FogStart");
        uFogEnd = GL20.glGetUniformLocation(program, "u_FogEnd");
        uFogDensity = GL20.glGetUniformLocation(program, "u_FogDensity");
    }

    /** Uploads the current fixed-function matrix {@code glMatrix} (GL_PROJECTION_MATRIX / GL_MODELVIEW_MATRIX). */
    void loadMatrix(int location, int glMatrix) {
        matrix.clear();
        GL11.glGetFloat(glMatrix, matrix);
        matrix.rewind();
        GL20.glUniformMatrix4(location, false, matrix);
    }

    void delete() {
        GL20.glDeleteProgram(program);
    }

    private static String withDefines(String src, String defines) {
        int eol = src.indexOf('\n');
        return src.substring(0, eol + 1) + defines + src.substring(eol + 1);
    }

    private static int compile(int type, String src) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Sodium shader failed to compile: " + log);
        }
        return shader;
    }

    private static String load(String name) {
        try (InputStream in = ChunkShader.class.getResourceAsStream("/assets/novomc/shaders/sodium/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing shader " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read shader " + name, e);
        }
    }
}
