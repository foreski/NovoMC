package com.novomc.client.module;

import com.novomc.client.NovoClient;

/**
 * A module that draws on the HUD. Its position is stored as a fraction of the
 * screen (so it survives GUI scale and window size changes) plus a scale.
 */
public abstract class HudModule extends Module {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;

    protected HudModule(String id) {
        super(id);
    }

    /** Size in unscaled GUI pixels, for the current content (or preview). */
    public abstract int width(boolean preview);

    public abstract int height(boolean preview);

    /** Draws at (0, 0). `preview` means the HUD editor: show sample data. */
    public abstract void render(boolean preview);

    /** False hides the module in game (e.g. Ping in singleplayer). */
    public boolean visible() {
        return true;
    }

    public float x() {
        return NovoClient.config().number(id(), "x", def.defaultX);
    }

    public float y() {
        return NovoClient.config().number(id(), "y", def.defaultY);
    }

    public float scale() {
        float s = NovoClient.config().number(id(), "scale", 1f);
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
    }

    public void setPosition(float x, float y) {
        NovoClient.config().setNumber(id(), "x", x);
        NovoClient.config().setNumber(id(), "y", y);
    }

    public void setScale(float scale) {
        NovoClient.config().setNumber(id(), "scale", Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)));
    }

    /** True while the module sits in its default column (never moved by the user). */
    public boolean stacked() {
        return def.stack != null && !NovoClient.config().has(id(), "x") && !NovoClient.config().has(id(), "y");
    }

    /** Forgets the user's position and size, returning to the default (or the stack). */
    public void resetPosition() {
        NovoClient.config().remove(id(), "x");
        NovoClient.config().remove(id(), "y");
        NovoClient.config().remove(id(), "scale");
    }
}
