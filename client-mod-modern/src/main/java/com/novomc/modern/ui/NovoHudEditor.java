package com.novomc.modern.ui;

import com.novomc.modern.HudModule;
import com.novomc.modern.NovoModern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The HUD editor (Right Shift), as on 1.8.9: the live HUD stays visible, each
 * mod gets an outline to drag, the wheel resizes it and right-click puts it
 * back in its default column. Positions save as the shared screen fractions.
 */
public class NovoHudEditor extends Screen {
    private static final int SNAP = 4;

    private final Screen parent;
    private HudModule dragging;
    private double grabX;
    private double grabY;
    private int guideX = -1;
    private int guideY = -1;

    public NovoHudEditor(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // Above the HUD, which draws in the same frame.
        g.nextStratum();
        if (guideX >= 0) {
            g.fill(guideX, 0, guideX + 1, height, 0x805B8CFF);
        }
        if (guideY >= 0) {
            g.fill(0, guideY, width, guideY + 1, 0x805B8CFF);
        }
        HudModule hover = at(mouseX, mouseY);
        for (HudModule h : NovoModern.hudModules()) {
            if (!h.isEnabled() || !h.visible()) {
                continue;
            }
            int[] b = NovoModern.bounds(h, width, height);
            boolean on = h == dragging || h == hover;
            g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, on ? 0x405B8CFF : 0x20FFFFFF);
            g.outline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, on ? NovoUi.ACCENT : 0xB0FFFFFF);
            if (on) {
                Component name = NovoUi.text(h.def.name + "  " + Math.round(h.scale() * 100) + "%");
                int ty = b[1] > 12 ? b[1] - 11 : b[1] + b[3] + 3;
                NovoUi.draw(g, name, b[0], ty, 0xFFFFFFFF);
            }
        }

        int cx = width / 2;
        int cy = height / 2;
        NovoUi.round(g, cx - 150, cy - 42, cx + 150, cy + 20, 0xC00C1019);
        NovoUi.centered(g, NovoUi.bold("Edit HUD"), cx, cy - 34, NovoUi.TEXT);
        NovoUi.centered(g, NovoUi.text("Drag to move  ·  Scroll to resize  ·  Right-click to reset"), cx, cy - 22, NovoUi.MUTED);
        button(g, cx - 82, cy - 6, 80, "Mods", mouseX, mouseY);
        button(g, cx + 2, cy - 6, 80, "Done", mouseX, mouseY);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 18;
        boolean primary = "Done".equals(label);
        NovoUi.round(g, x, y, x + w, y + 18, primary ? (hover ? 0xFF6E9BFF : NovoUi.ACCENT) : (hover ? 0x40FFFFFF : 0x26FFFFFF));
        NovoUi.centered(g, NovoUi.text(label), x + w / 2, y + 5, 0xFFFFFFFF);
    }

    private HudModule at(double x, double y) {
        HudModule found = null;
        for (HudModule h : NovoModern.hudModules()) {
            if (h.isEnabled() && h.visible()) {
                int[] b = NovoModern.bounds(h, width, height);
                if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                    found = h;
                }
            }
        }
        return found;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int cx = width / 2;
        int cy = height / 2;
        double x = event.x();
        double y = event.y();
        if (event.button() == 0 && y >= cy - 6 && y < cy + 12) {
            if (x >= cx - 82 && x < cx - 2) {
                minecraft.gui.setScreen(new NovoMods(this));
                return true;
            }
            if (x >= cx + 2 && x < cx + 82) {
                onClose();
                return true;
            }
        }
        HudModule h = at(x, y);
        if (h == null) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == 1) {
            NovoModern.config().remove(h.id(), "x");
            NovoModern.config().remove(h.id(), "y");
            NovoModern.config().remove(h.id(), "scale");
            NovoModern.config().save();
        } else if (event.button() == 0) {
            int[] b = NovoModern.bounds(h, width, height);
            dragging = h;
            grabX = x - b[0];
            grabY = y - b[1];
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) {
            return super.mouseDragged(event, dx, dy);
        }
        int[] b = NovoModern.bounds(dragging, width, height);
        int left = (int) Math.round(event.x() - grabX);
        int top = (int) Math.round(event.y() - grabY);
        left = Math.max(0, Math.min(width - b[2], left));
        top = Math.max(0, Math.min(height - b[3], top));
        // Snap the middle of the mod to the middle of the screen.
        guideX = Math.abs(left + b[2] / 2 - width / 2) <= SNAP ? width / 2 : -1;
        if (guideX >= 0) {
            left = width / 2 - b[2] / 2;
        }
        guideY = Math.abs(top + b[3] / 2 - height / 2) <= SNAP ? height / 2 : -1;
        if (guideY >= 0) {
            top = height / 2 - b[3] / 2;
        }
        float fx = (left + (dragging.def.centered ? b[2] / 2f : 0)) / width;
        NovoModern.config().setNumber(dragging.id(), "x", fx);
        NovoModern.config().setNumber(dragging.id(), "y", top / (float) height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            guideX = -1;
            guideY = -1;
            NovoModern.config().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        HudModule m = at(x, y);
        if (m != null && v != 0) {
            float s = Math.round((m.scale() + Math.signum(v) * 0.1f) * 10) / 10f;
            NovoModern.config().setNumber(m.id(), "scale", Math.max(HudModule.MIN_SCALE, Math.min(HudModule.MAX_SCALE, s)));
            NovoModern.config().save();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
