#version 330 core

// Ported from Sodium 0.5 (block_layer_opaque.vsh, chunk_vertex.glsl, chunk_matrices.glsl).
// Copyright (C) JellySquid and Sodium contributors. GNU LGPL v3. Modified for NovoMC.
// VERT_POS_SCALE, VERT_POS_OFFSET and VERT_TEX_SCALE are defined by ChunkShader.

in uvec4 a_PosId;
in vec4 a_Color;
in vec2 a_TexCoord;
in ivec2 a_LightCoord;

uniform mat4 u_ProjectionMatrix;
uniform mat4 u_ModelViewMatrix;
uniform vec3 u_RegionOffset;
uniform sampler2D u_LightTex;

out vec4 v_Color;
out vec2 v_TexCoord;
out float v_FragDistance;

uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

void main() {
    vec3 position = vec3(a_PosId.xyz) * VERT_POS_SCALE + VERT_POS_OFFSET;
    uint drawId = (a_PosId.w >> 8u) & 0xFFu;

    // Transform the chunk-local vertex position into camera-relative world space
    position += u_RegionOffset + vec3(_get_relative_chunk_coord(drawId)) * 16.0;

    vec4 viewPos = u_ModelViewMatrix * vec4(position, 1.0);
    gl_Position = u_ProjectionMatrix * viewPos;
    v_FragDistance = length(viewPos.xyz);

    // 1.8.9 lightmap coordinates are 0..240; vanilla's texture matrix maps them as (uv + 8) / 256
    vec4 light = texture(u_LightTex, (vec2(a_LightCoord) + 8.0) / 256.0);
    v_Color = a_Color * light;
    v_TexCoord = a_TexCoord * VERT_TEX_SCALE;
}
