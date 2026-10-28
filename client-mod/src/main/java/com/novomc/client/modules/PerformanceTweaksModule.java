package com.novomc.client.modules;

import com.novomc.client.module.Module;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

/**
 * Faster settings vanilla 1.8.9 leaves off: uncapped frame rate without VSync,
 * VBO chunk rendering, no entity shadows, no clouds and (optionally) fast
 * graphics. The user's own values come back when a tweak is switched off.
 */
public class PerformanceTweaksModule extends Module {
    /** Vanilla's "Unlimited" frame limit. */
    private static final int UNLIMITED = (int) GameSettings.Options.FRAMERATE_LIMIT.getValueMax();

    private Boolean savedVbo;
    private Boolean savedShadows;
    private Boolean savedVsync;
    private Integer savedLimit;
    private Integer savedClouds;
    private Boolean savedFancy;
    private static volatile boolean noAnimations;
    private static volatile int chunkUpdatesPerSecond;
    private static long nextChunkUpdateNanos;

    /** Whether texture animations (water, lava, fire, portals) are switched off. */
    public static boolean noAnimations() {
        return noAnimations;
    }

    public PerformanceTweaksModule() {
        super("performance_tweaks");
    }

    @Override
    protected void onEnable() {
        onSettingsChanged();
    }

    @Override
    public void onSettingsChanged() {
        GameSettings gs = mc.gameSettings;
        noAnimations = bool("noAnimations");
        chunkUpdatesPerSecond = Math.max(0, Math.min(120, integer("chunkUpdateLimit")));
        synchronized (PerformanceTweaksModule.class) {
            nextChunkUpdateNanos = 0L;
        }
        if (bool("unlimitedFps")) {
            if (savedVsync == null) {
                savedVsync = gs.enableVsync;
                savedLimit = gs.limitFramerate;
            }
            setVsync(false);
            gs.limitFramerate = UNLIMITED;
        } else {
            restoreFrameRate();
        }
        if (bool("vbo") && OpenGlHelper.vboSupported) {
            if (savedVbo == null) {
                savedVbo = gs.useVbo;
            }
            setVbo(true);
        } else {
            restoreVbo();
        }
        if (bool("noEntityShadows")) {
            if (savedShadows == null) {
                savedShadows = gs.entityShadows;
            }
            gs.entityShadows = false;
        } else {
            restoreShadows();
        }
        if (bool("noClouds")) {
            if (savedClouds == null) {
                savedClouds = gs.clouds;
            }
            gs.clouds = 0;
        } else {
            restoreClouds();
        }
        if (bool("fastGraphics")) {
            if (savedFancy == null) {
                savedFancy = gs.fancyGraphics;
            }
            setFancy(false);
        } else {
            restoreFancy();
        }
    }

    @Override
    protected void onDisable() {
        noAnimations = false;
        chunkUpdatesPerSecond = 0;
        synchronized (PerformanceTweaksModule.class) {
            nextChunkUpdateNanos = 0L;
        }
        restoreFrameRate();
        restoreVbo();
        restoreShadows();
        restoreClouds();
        restoreFancy();
    }

    private void setVsync(boolean on) {
        mc.gameSettings.enableVsync = on;
        Display.setVSyncEnabled(on);
    }

    private void setVbo(boolean on) {
        if (mc.gameSettings.useVbo != on) {
            mc.gameSettings.useVbo = on;
            // Chunk renderers must be rebuilt to switch, like the video settings screen does.
            mc.renderGlobal.loadRenderers();
        }
    }

    private void setFancy(boolean on) {
        if (mc.gameSettings.fancyGraphics != on) {
            mc.gameSettings.fancyGraphics = on;
            mc.renderGlobal.loadRenderers();
        }
    }

    private void restoreFrameRate() {
        if (savedVsync != null) {
            setVsync(savedVsync);
            mc.gameSettings.limitFramerate = savedLimit;
            savedVsync = null;
            savedLimit = null;
        }
    }

    private void restoreVbo() {
        if (savedVbo != null) {
            setVbo(savedVbo);
            savedVbo = null;
        }
    }

    private void restoreShadows() {
        if (savedShadows != null) {
            mc.gameSettings.entityShadows = savedShadows;
            savedShadows = null;
        }
    }

    private void restoreClouds() {
        if (savedClouds != null) {
            mc.gameSettings.clouds = savedClouds;
            savedClouds = null;
        }
    }

    private void restoreFancy() {
        if (savedFancy != null) {
            setFancy(savedFancy);
            savedFancy = null;
        }
    }

    /** Pace background chunk rebuild starts without ever blocking the render thread. */
    public static void awaitChunkUpdateSlot() {
        int rate = chunkUpdatesPerSecond;
        if (rate <= 0) {
            return;
        }
        long now = System.nanoTime();
        long slot;
        synchronized (PerformanceTweaksModule.class) {
            now = System.nanoTime();
            slot = Math.max(now, nextChunkUpdateNanos);
            nextChunkUpdateNanos = slot + 1_000_000_000L / rate;
        }
        long delay = slot - now;
        if (delay > 0L) {
            LockSupport.parkNanos(delay);
        }
    }
}
