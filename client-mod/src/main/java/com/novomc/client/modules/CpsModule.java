package com.novomc.client.modules;

import com.novomc.client.module.TextHud;

public class CpsModule extends TextHud {
    public CpsModule() {
        super("cps");
    }

    @Override
    protected String[] lines(boolean preview) {
        int l = CpsTracker.INSTANCE.left();
        if (!bool("showRight")) {
            return new String[] {l + " CPS"};
        }
        return new String[] {l + " | " + CpsTracker.INSTANCE.right() + " CPS"};
    }
}
