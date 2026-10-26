package com.novomc.client.modules;

import com.novomc.client.module.Module;

/** Caps how many particles exist at once and drops far ones (enforced in MixinEffectRenderer). */
public class ParticleLimiterModule extends Module {
    private static volatile int limit = -1;
    private static volatile double maxDistanceSq;

    public ParticleLimiterModule() {
        super("particle_limiter");
    }

    /** Particles farther than this (squared) from the camera are not spawned. */
    public static double maxDistanceSq() {
        return maxDistanceSq;
    }

    /** The current cap, or -1 when the module is off. */
    public static int limit() {
        return limit;
    }

    @Override
    protected void onEnable() {
        onSettingsChanged();
    }

    @Override
    public void onSettingsChanged() {
        limit = Math.max(100, integer("max"));
        double d = number("distance");
        maxDistanceSq = d > 0 ? d * d : Double.MAX_VALUE;
    }

    @Override
    protected void onDisable() {
        limit = -1;
    }
}
