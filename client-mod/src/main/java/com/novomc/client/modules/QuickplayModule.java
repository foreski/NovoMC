package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.gui.GuiQuickplay;
import com.novomc.client.module.Module;

/** Quickplay: a key (J by default) opens a menu that joins Hypixel games and lobbies in one click. */
public class QuickplayModule extends Module {
    public QuickplayModule() {
        super("quickplay");
    }

    @Override
    public void onTick() {
        while (NovoClient.QUICKPLAY_KEY.isPressed()) {
            if (mc.currentScreen == null && mc.thePlayer != null) {
                mc.displayGuiScreen(new GuiQuickplay(null));
            }
        }
    }
}
