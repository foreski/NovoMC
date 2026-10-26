package com.novomc.client.modules;

import com.novomc.client.module.TextHud;

/** In-game day, from the world's total age (unaffected by /time set or Time Changer). */
public class DayCounterModule extends TextHud {
    public DayCounterModule() {
        super("day_counter");
    }

    @Override
    protected String[] lines(boolean preview) {
        long day = mc.theWorld != null ? mc.theWorld.getTotalWorldTime() / 24000 + 1 : 1;
        return new String[] {"Day " + day};
    }
}
