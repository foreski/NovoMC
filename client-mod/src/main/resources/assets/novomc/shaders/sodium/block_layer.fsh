#version 330 core

// Ported from Sodium 0.5 (block_layer_opaque.fsh, fog.glsl).
// Copyright (C) JellySquid and Sodium contributors. GNU LGPL v3. Modified for NovoMC:
// fog follows the fixed-function state 1.8.9 sets (off, linear, exp, exp2), and the
// alpha test stays fixed-function since 1.8.9 runs a compatibility context.

in vec4 v_Color;
in vec2 v_TexCoord;
in float v_FragDistance;

uniform sampler2D u_BlockTex;

uniform int u_FogMode; // 0 off, 1 linear, 2 exp, 3 exp2
uniform vec4 u_FogColor;
uniform float u_FogStart;
uniform float u_FogEnd;
uniform float u_FogDensity;

out vec4 fragColor;

float _fogFactor(float d) {
    if (u_FogMode == 1) {
        return clamp((u_FogEnd - d) / (u_FogEnd - u_FogStart), 0.0, 1.0);
    } else if (u_FogMode == 2) {
        return clamp(exp(-u_FogDensity * d), 0.0, 1.0);
    } else if (u_FogMode == 3) {
        float f = u_FogDensity * d;
        return clamp(exp(-f * f), 0.0, 1.0);
    }
    return 1.0;
}

void main() {
    vec4 color = texture(u_BlockTex, v_TexCoord) * v_Color;
    // Alpha value of the fragment cannot be modified by fog
    fragColor = vec4(mix(u_FogColor.rgb, color.rgb, _fogFactor(v_FragDistance)), color.a);
}
