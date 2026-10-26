package com.novomc.client.module;

import com.novomc.client.util.Draw;

/**
 * A HUD module showing one or more lines of text in an optional dark box.
 * Uses the `textColor` and `background` settings when the module has them.
 */
public abstract class TextHud extends HudModule {
    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;
    private static final int LINE = 10;

    protected TextHud(String id) {
        super(id);
    }

    protected abstract String[] lines(boolean preview);

    private boolean hasBackground() {
        return def.defaults.containsKey("background") && bool("background");
    }

    @Override
    public int width(boolean preview) {
        int w = 0;
        for (String line : lines(preview)) {
            w = Math.max(w, Draw.width(line));
        }
        return w + PAD_X * 2;
    }

    @Override
    public int height(boolean preview) {
        return lines(preview).length * LINE + PAD_Y * 2 - 1;
    }

    @Override
    public void render(boolean preview) {
        String[] lines = lines(preview);
        if (hasBackground()) {
            Draw.rect(0, 0, width(preview), height(preview), 0x6F000000);
        }
        int color = def.defaults.containsKey("textColor") ? color("textColor") : 0xFFFFFFFF;
        for (int i = 0; i < lines.length; i++) {
            Draw.text(lines[i], PAD_X, PAD_Y + i * LINE, color);
        }
    }
}
