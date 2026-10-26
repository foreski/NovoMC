package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Shows clear skies, rain or a storm. Client-side only. */
public class WeatherChangerModule extends Module {
    public WeatherChangerModule() {
        super("weather_changer");
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!isEnabled() || event.phase != TickEvent.Phase.START || mc.theWorld == null) {
            return;
        }
        String weather = choice("weather");
        mc.theWorld.setRainStrength("clear".equals(weather) ? 0f : 1f);
        mc.theWorld.setThunderStrength("storm".equals(weather) ? 1f : 0f);
    }

    @Override
    protected void onDisable() {
        // Back to the real weather.
        if (mc.theWorld != null) {
            mc.theWorld.setRainStrength(mc.theWorld.getWorldInfo().isRaining() ? 1f : 0f);
            mc.theWorld.setThunderStrength(mc.theWorld.getWorldInfo().isThundering() ? 1f : 0f);
        }
    }
}
