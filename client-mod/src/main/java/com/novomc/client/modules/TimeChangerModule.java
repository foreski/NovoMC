package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Shows a fixed time of day. Client-side only; the server keeps its own time. */
public class TimeChangerModule extends Module {
    public TimeChangerModule() {
        super("time_changer");
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        // Every frame, so the server's time updates never show through.
        if (isEnabled() && event.phase == TickEvent.Phase.START && mc.theWorld != null) {
            mc.theWorld.setWorldTime(integer("time"));
        }
    }
}
