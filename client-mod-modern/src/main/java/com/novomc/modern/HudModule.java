package com.novomc.modern;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A mod that draws on the HUD. Positions use the same screen fractions,
 * default columns and stacking as 1.8.9, so a layout carries over.
 */
public abstract class HudModule extends Module {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;

    protected HudModule(String id) {
        super(id);
    }

    public abstract int width();

    public abstract int height();

    /** Draws at (0, 0); the caller has moved and scaled the pose. */
    public abstract void render(GuiGraphicsExtractor g);

    /** False hides the module in game (e.g. Ping in singleplayer). */
    public boolean visible() {
        return true;
    }

    public float x() {
        return NovoModern.config().number(id(), "x", def.defaultX);
    }

    public float y() {
        return NovoModern.config().number(id(), "y", def.defaultY);
    }

    public float scale() {
        float s = NovoModern.config().number(id(), "scale", 1f);
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
    }

    /** True while the module sits in its default column (never moved by the user). */
    public boolean stacked() {
        return def.stack != null && !NovoModern.config().has(id(), "x") && !NovoModern.config().has(id(), "y");
    }
}
