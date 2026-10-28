package com.novomc.modern;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A HUD mod showing lines of text in an optional dark box, using the
 * `textColor` and `background` settings when it has them (as on 1.8.9).
 */
public abstract class TextHud extends HudModule {
    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;
    private static final int LINE = 10;

    protected TextHud(String id) {
        super(id);
    }

    protected abstract String[] lines();

    private boolean hasBackground() {
        return def.defaults.containsKey("background") && bool("background");
    }

    @Override
    public int width() {
        int w = 0;
        for (String line : lines()) {
            w = Math.max(w, mc.font.width(line));
        }
        return w + PAD_X * 2;
    }

    @Override
    public int height() {
        return lines().length * LINE + PAD_Y * 2 - 1;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        String[] lines = lines();
        if (hasBackground()) {
            g.fill(0, 0, width(), height(), 0x6F000000);
        }
        int color = def.defaults.containsKey("textColor") ? color("textColor") : 0xFFFFFFFF;
        for (int i = 0; i < lines.length; i++) {
            g.text(mc.font, lines[i], PAD_X, PAD_Y + i * LINE, color, true);
        }
    }
}
