package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import net.minecraft.client.multiplayer.ServerData;

public class ServerAddressModule extends TextHud {
    public ServerAddressModule() {
        super("server_address");
    }

    @Override
    public boolean visible() {
        return mc.getCurrentServerData() != null || bool("showInSingleplayer");
    }

    @Override
    protected String[] lines(boolean preview) {
        ServerData data = mc.getCurrentServerData();
        return new String[] {data != null ? data.serverIP : preview ? "play.example.net" : "Singleplayer"};
    }
}
