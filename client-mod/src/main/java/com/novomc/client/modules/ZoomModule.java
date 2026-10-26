package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.module.Module;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Hold the zoom key (C) to zoom; scroll while zoomed to change how far. */
public class ZoomModule extends Module {
    private boolean zooming;
    private float factor = 4f;
    /** Current zoom, eased toward the target when smooth zoom is on. */
    private float current = 1f;
    private Boolean savedSmoothCamera;
    private long lastFrame = System.nanoTime();

    public ZoomModule() {
        super("zoom");
    }

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier event) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        boolean want = isEnabled() && NovoClient.ZOOM_KEY.isKeyDown() && mc.currentScreen == null;
        if (want && !zooming) {
            zooming = true;
            factor = number("factor");
            if (bool("cinematic")) {
                savedSmoothCamera = mc.gameSettings.smoothCamera;
                mc.gameSettings.smoothCamera = true;
            }
        } else if (!want && zooming) {
            stop();
        }

        float target = zooming ? factor : 1f;
        current = isEnabled() && bool("smooth") ? current + (target - current) * Math.min(1f, dt * 14f) : target;
        if (Math.abs(current - 1f) < 0.002f) {
            current = 1f;
        } else {
            event.setFOV(event.getFOV() / current);
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (zooming && event.dwheel != 0 && bool("scroll")) {
            factor = Math.max(1.5f, Math.min(30f, event.dwheel > 0 ? factor * 1.2f : factor / 1.2f));
            event.setCanceled(true);
        }
    }

    private void stop() {
        zooming = false;
        if (savedSmoothCamera != null) {
            mc.gameSettings.smoothCamera = savedSmoothCamera;
            savedSmoothCamera = null;
        }
    }

    @Override
    protected void onDisable() {
        stop();
    }
}
