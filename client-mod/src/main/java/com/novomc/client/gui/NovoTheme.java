package com.novomc.client.gui;

import com.novomc.client.util.Draw;
import net.minecraft.client.gui.Gui;

/**
 * Draws vanilla's own widgets (buttons, sliders, text fields, lists and the
 * dirt background) in NovoMC's style, so every remaining vanilla screen and
 * most mod screens match NovoMC's custom ones. Hooked in by the Gui* mixins.
 */
public final class NovoTheme {
    public static volatile boolean enabled;

    static final int PANEL = 0xFF0C1019;
    static final int TEXT = 0xFFE8ECF4;
    static final int MUTED = 0xFF8A93A6;
    static final int ACCENT = NovoButton.ACCENT;

    private NovoTheme() {}

    /** Fraction of the way to move an animated value this frame, for a frame time of {@code dtNanos}. */
    public static float step(long dtNanos) {
        return 1 - (float) Math.exp(-Math.min(0.1, dtNanos / 1e9) * 16);
    }

    /**
     * A vanilla button. {@code state} is vanilla's texture row (0 disabled, 1 normal,
     * 2 hovered); {@code hover} is the eased 0..1 hover amount.
     */
    public static void button(int x, int y, int w, int h, int state, boolean slider, float hover) {
        float radius = Math.min(5, h / 2f);
        if (slider) {
            Draw.round(x, y, x + w, y + h, radius, Draw.blend(0x99101521, 0xC0162036, hover));
            Draw.roundOutline(x, y, x + w, y + h, radius, Draw.blend(0x24FFFFFF, Draw.withAlpha(ACCENT, 0x80), hover));
            return;
        }
        Draw.round(x, y, x + w, y + h, radius, state == 0 ? 0x66101521 : Draw.blend(0x99101521, 0xD01B2540, hover));
        if (hover > 0.02f) {
            // The accent underline grows out from the centre.
            float half = (w / 2f - 1) * hover;
            Draw.round(x + w / 2f - half, y + h - 2, x + w / 2f + half, y + h, 1, Draw.withAlpha(ACCENT, Math.round(hover * 200)));
        }
        Draw.roundOutline(x, y, x + w, y + h, radius, Draw.blend(0x24FFFFFF, Draw.withAlpha(ACCENT, 0xCC), hover));
    }

    /** Button label, centred, in NovoMC colours instead of vanilla's grey / yellow. */
    public static void label(String text, int cx, int y, int vanillaColor) {
        int color = vanillaColor == 0xA0A0A0 ? MUTED : vanillaColor == 0xFFFFA0 ? 0xFFFFFFFF : TEXT;
        Draw.text(text, cx - Draw.width(text) / 2f, y, color);
    }

    /**
     * Slider fill and knob along the bottom edge, clear of the label vanilla
     * draws in the middle; {@code knobX} is where vanilla draws its 8px knob.
     */
    public static void sliderKnob(int x, int y, int w, int h, int knobX) {
        float cy = y + h - 4;
        float kx = knobX + 4;
        Draw.round(x + 6, cy - 1, x + w - 6, cy + 1, 1, 0xFF2A3142);
        Draw.round(x + 6, cy - 1, kx, cy + 1, 1, ACCENT);
        Draw.round(kx - 3, cy - 3, kx + 3, cy + 3, 3, 0xFFFFFFFF);
    }

    public static void fieldBorder(int l, int t, int r, int b, boolean focused) {
        Draw.round(l, t, r, b, 3, 0xFF0A0E16);
        Draw.roundOutline(l, t, r, b, 3, focused ? Draw.withAlpha(ACCENT, 0xCC) : 0x30FFFFFF);
    }

    /** Replaces the dirt background of menus outside a world. */
    public static void background(int width, int height) {
        Gui.drawRect(0, 0, width, height, 0xFF070A12);
        // Soft brand-coloured glows in two corners, drifting slowly (a 20 s loop).
        double t = System.currentTimeMillis() % 20000L / 20000.0 * Math.PI * 2;
        float dx = (float) Math.sin(t) * width * 0.04f;
        float dy = (float) Math.cos(t) * height * 0.04f;
        for (int i = 0; i < 6; i++) {
            float s = 1 - i / 6f;
            Draw.round(dx - width * 0.2f * s, dy - height * 0.3f * s, dx + width * 0.5f * s, dy + height * 0.5f * s, 60,
                    Draw.withAlpha(ACCENT, 6));
            Draw.round(width - dx - width * 0.5f * s, height - dy - height * 0.5f * s, width - dx + width * 0.2f * s,
                    height - dy + height * 0.3f * s, 60, Draw.withAlpha(0xFFB45BFF, 6));
        }
    }

    private static final long FADE_NANOS = 180_000_000L;
    private static long openedAt;

    /** A menu was opened: start its fade-in. */
    public static void opened() {
        openedAt = System.nanoTime();
    }

    /** Drawn over a freshly opened menu: a dark veil that clears with an ease-out over 180 ms. */
    public static void fadeIn(int width, int height) {
        long age = System.nanoTime() - openedAt;
        if (age >= FADE_NANOS) {
            return;
        }
        float t = age / (float) FADE_NANOS;
        float veil = (1 - t) * (1 - t);
        Gui.drawRect(0, 0, width, height, Draw.withAlpha(0xFF070A12, Math.round(veil * 255)));
    }

    /** List backgrounds (server, world, language and control lists). */
    public static void listBackground(int l, int t, int r, int b) {
        Gui.drawRect(l, t, r, b, 0xCC0C1019);
    }

    /** The bars above and below a list. */
    public static void listBar(int l, int t, int r, int b) {
        Gui.drawRect(l, t, r, b, PANEL);
    }
}
