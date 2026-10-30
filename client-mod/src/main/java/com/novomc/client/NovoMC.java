package com.novomc.client;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** Forge entry point. Everything else lives in {@link NovoClient}. */
@Mod(modid = NovoMC.MODID, name = "Novo Client", version = NovoMC.VERSION, acceptedMinecraftVersions = "[1.8.9]")
public class NovoMC {
    public static final String MODID = "novomc";
    public static final String VERSION = "0.4.5";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NovoClient.init();
    }
}
