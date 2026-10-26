package com.novomc.client.gui;

import com.novomc.client.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

/**
 * NovoMC's button: rounded dark glass with a brand-blue edge and glow on
 * hover, an optional icon and an optional on/off pill.
 */
public class NovoButton extends GuiButton {
    public static final int ACCENT = 0xFF5B8CFF;

    private ResourceLocation icon;
    private String tooltip;
    /** Non-null shows an ON/OFF pill on the right. */
    private Boolean toggled;
    private boolean uppercase = false;
    private float hover;

    public NovoButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    public NovoButton icon(ResourceLocation icon) {
        this.icon = icon;
        return this;
    }

    public NovoButton tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public NovoButton toggled(Boolean on) {
        this.toggled = on;
        return this;
    }

    public NovoButton plainCase() {
        this.uppercase = false;
        return this;
    }

    public String tooltip() {
        return hovered ? tooltip : null;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        hover += ((hovered && enabled ? 1f : 0f) - hover) * 0.3f;

        int right = xPosition + width;
        int bottom = yPosition + height;
        float radius = Math.min(5, height / 2f);
        Draw.round(xPosition, yPosition, right, bottom, radius, Draw.blend(0x99101521, 0xD01B2540, hover));
        if (hover > 0.02f) {
            // Soft accent glow along the bottom edge on hover.
            Draw.round(xPosition + 1, bottom - 2, right - 1, bottom, 1, Draw.withAlpha(ACCENT, Math.round(hover * 200)));
        }
        Draw.roundOutline(xPosition, yPosition, right, bottom, radius, Draw.blend(0x24FFFFFF, Draw.withAlpha(ACCENT, 0xCC), hover));
        int textColor = enabled ? Draw.blend(0xFFC9D1E0, 0xFFFFFFFF, hover) : 0xFF6A7080;

        if (icon != null) {
            float size = Math.min(height - 8, 12);
            float ix = displayString.isEmpty() ? xPosition + (width - size) / 2f : xPosition + 7;
            Draw.icon(icon, ix, yPosition + (height - size) / 2f, size, textColor);
        }
        if (!displayString.isEmpty()) {
            String text = uppercase ? displayString.toUpperCase() : displayString;
            float spacing = uppercase ? 1f : 0f;
            float w = Draw.spacedWidth(text, spacing);
            float tx = toggled != null ? xPosition + 8 : xPosition + (width - w) / 2f;
            Draw.spaced(text, tx, yPosition + (height - 8) / 2f, textColor, spacing);
        }
        if (toggled != null) {
            String state = toggled ? "ON" : "OFF";
            int pillW = 22;
            int px = right - pillW - 4;
            int py = yPosition + (height - 10) / 2;
            Draw.round(px, py, px + pillW, py + 10, 5, toggled ? 0xCC2E9E5B : 0x80404650);
            Draw.text(state, px + (pillW - Draw.width(state)) / 2f, py + 1, 0xFFFFFFFF);
        }
    }
}
