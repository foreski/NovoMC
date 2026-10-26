package com.novomc.client.modules;

import com.novomc.client.module.TextHud;

/** Time in the current world or server (counted from when you joined, or turned this on). */
public class PlaytimeModule extends TextHud {
    private long joinedAt;

    public PlaytimeModule() {
        super("playtime");
    }

    @Override
    public void onTick() {
        if (mc.theWorld == null) {
            joinedAt = 0;
        } else if (joinedAt == 0) {
            joinedAt = System.currentTimeMillis();
        }
    }

    @Override
    protected String[] lines(boolean preview) {
        long s = joinedAt == 0 ? 0 : (System.currentTimeMillis() - joinedAt) / 1000;
        return new String[] {String.format("Playtime %d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)};
    }
}
