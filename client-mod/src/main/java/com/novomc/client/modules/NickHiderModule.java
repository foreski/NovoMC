package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Replaces your username in all text you see (MixinFontRenderer). Only on your screen. */
public class NickHiderModule extends Module {
    private static volatile String name;
    private static volatile String nick;

    public NickHiderModule() {
        super("nick_hider");
    }

    public static String replace(String text) {
        String n = name;
        if (n == null || text == null || !text.contains(n)) {
            return text;
        }
        return text.replace(n, nick);
    }

    @Override
    public void onSettingsChanged() {
        String chosen = text("nick").trim();
        nick = chosen.isEmpty() ? "You" : chosen;
        name = mc.getSession().getUsername();
    }

    @Override
    protected void onDisable() {
        name = null;
    }
}
