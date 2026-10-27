package com.novomc.client.render;

/**
 * Fast Math, inspired by OptiFine's option of the same name; NovoMC's own code.
 * A 4096-entry sine table (16 KB) instead of vanilla's 65536 entries (256 KB)
 * stays in the CPU cache. Precision drops from 0.0001 to 0.0015 radians.
 */
public final class FastMath {
    public static volatile boolean enabled;

    private static final float[] SIN = new float[4096];
    private static final float SCALE = 4096 / (float) (Math.PI * 2);

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i / SCALE);
        }
    }

    private FastMath() {}

    public static float sin(float value) {
        return SIN[(int) (value * SCALE) & 4095];
    }

    public static float cos(float value) {
        return SIN[(int) (value * SCALE + 1024) & 4095];
    }
}
