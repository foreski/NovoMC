package com.novomc.client.modules;

import com.novomc.client.module.TextHud;

public class MemoryModule extends TextHud {
    public MemoryModule() {
        super("memory");
    }

    @Override
    protected String[] lines(boolean preview) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        return new String[] {"Mem " + (used * 100 / Math.max(1, max)) + "% (" + used + "/" + max + " MB)"};
    }
}
