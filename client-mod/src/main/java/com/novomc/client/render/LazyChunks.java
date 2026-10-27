package com.novomc.client.render;

/**
 * Lazy Chunk Loading, inspired by OptiFine's option of the same name; NovoMC's own
 * code. In singleplayer the built-in server loads and sends at most 10 chunks per
 * player per tick; this halves that so loading is spread over more ticks and
 * causes fewer stutters while flying or teleporting.
 */
public final class LazyChunks {
    public static volatile boolean enabled;

    private LazyChunks() {}

    public static int chunksPerTick() {
        return enabled ? 5 : 10;
    }
}
