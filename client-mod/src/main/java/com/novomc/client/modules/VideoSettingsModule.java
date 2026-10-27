package com.novomc.client.modules;

import com.novomc.client.gui.GuiNovoVideo;
import com.novomc.client.mixin.accessor.GuiVideoSettingsAccessor;
import com.novomc.client.module.Module;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Replaces Options > Video Settings with NovoMC's screen (vanilla options plus NovoMC's rendering options). */
public class VideoSettingsModule extends Module {
    public VideoSettingsModule() {
        super("video_settings");
    }

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        if (isEnabled() && event.gui != null && event.gui.getClass() == GuiVideoSettings.class) {
            event.gui = new GuiNovoVideo(((GuiVideoSettingsAccessor) event.gui).novomc$getParent());
        }
    }
}
