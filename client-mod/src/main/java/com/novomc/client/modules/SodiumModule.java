package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.sodium.SodiumRenderer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * NovoMC's port of Sodium's chunk renderer (see the sodium package). Needs
 * OpenGL 3.3 and VBO chunk rendering; otherwise vanilla rendering is used.
 */
public class SodiumModule extends Module {
    public SodiumModule() {
        super("sodium");
    }

    @Override
    protected void onEnable() {
        SodiumRenderer.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        SodiumRenderer.setEnabled(false);
    }

    @SubscribeEvent
    public void onDebugText(RenderGameOverlayEvent.Text event) {
        if (isEnabled() && mc.gameSettings.showDebugInfo && SodiumRenderer.isActive()) {
            event.left.add(SodiumRenderer.debugLine());
        }
    }
}
