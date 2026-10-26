package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Hold the snaplook key to see behind you (front third-person view). */
public class SnaplookModule extends Module {
    private int savedView = -1;

    public SnaplookModule() {
        super("snaplook");
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        boolean want = isEnabled() && NovoClient.SNAPLOOK_KEY.isKeyDown() && mc.currentScreen == null;
        if (want && savedView < 0) {
            savedView = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 2;
        } else if (!want && savedView >= 0) {
            restore();
        }
    }

    private void restore() {
        mc.gameSettings.thirdPersonView = savedView;
        savedView = -1;
    }

    @Override
    protected void onDisable() {
        if (savedView >= 0) {
            restore();
        }
    }
}
