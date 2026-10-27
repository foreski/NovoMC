package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * NovoMC's loading screens (connecting, downloading terrain, loading a world):
 * the logo fades in over a spinning ring. Drawn by MixinGuiConnecting,
 * MixinGuiDownloadTerrain and MixinLoadingScreenRenderer. The startup splash
 * is themed by the launcher (Forge draws it before mods load).
 */
public class LoadingScreenModule extends Module {
    private static volatile boolean on;

    public LoadingScreenModule() {
        super("loading_screen");
    }

    public static boolean on() {
        return on;
    }

    /** The server being joined, by its server-list name when it has one. */
    public static String serverName() {
        ServerData data = Minecraft.getMinecraft().getCurrentServerData();
        if (data == null) {
            return "server";
        }
        return data.serverName != null && !data.serverName.isEmpty() && !"Minecraft Server".equals(data.serverName)
                ? data.serverName : data.serverIP;
    }

    @Override
    protected void onEnable() {
        on = true;
    }

    @Override
    protected void onDisable() {
        on = false;
    }
}
