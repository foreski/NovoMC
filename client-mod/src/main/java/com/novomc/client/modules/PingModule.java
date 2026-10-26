package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import net.minecraft.client.network.NetworkPlayerInfo;

public class PingModule extends TextHud {
    public PingModule() {
        super("ping");
    }

    private int ping() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return 0;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info != null ? info.getResponseTime() : 0;
    }

    @Override
    public boolean visible() {
        return !mc.isSingleplayer() || bool("showInSingleplayer");
    }

    @Override
    protected String[] lines(boolean preview) {
        return new String[] {(preview && mc.isSingleplayer() ? 42 : ping()) + " ms"};
    }
}
