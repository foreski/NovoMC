package com.novomc.client.gui;

import com.novomc.client.NovoClient;
import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

/**
 * Right Shift screen: drag HUD modules to move them, scroll over one to
 * resize it, right-click to reset it. Positions save when the screen closes.
 */
public class GuiHudEditor extends GuiScreen {
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int SNAP = 4;

    private HudModule dragging;
    private int grabX;
    private int grabY;
    private boolean snappedX;
    private boolean snappedY;

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new NovoButton(0, width / 2 - 154, height - 30, 100, 20, "Mods"));
        buttonList.add(new NovoButton(2, width / 2 - 50, height - 30, 100, 20, "Packs"));
        buttonList.add(new NovoButton(1, width / 2 + 54, height - 30, 100, 20, "Done"));
    }

    private List<HudModule> enabled() {
        List<HudModule> list = NovoClient.hudModules();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!list.get(i).isEnabled()) {
                list.remove(i);
            }
        }
        return list;
    }

    /** Topmost enabled module under the mouse, or null. */
    private HudModule at(int mouseX, int mouseY) {
        ScaledResolution sr = new ScaledResolution(mc);
        List<HudModule> list = enabled();
        for (int i = list.size() - 1; i >= 0; i--) {
            int[] b = NovoClient.bounds(list.get(i), sr, true);
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                return list.get(i);
            }
        }
        return null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Draw.rect(0, 0, width, height, 0x66000000);
        if (dragging != null && snappedX) {
            Draw.rect(width / 2, 0, width / 2 + 1, height, Draw.withAlpha(ACCENT, 0x99));
        }
        if (dragging != null && snappedY) {
            Draw.rect(0, height / 2, width, height / 2 + 1, Draw.withAlpha(ACCENT, 0x99));
        }

        ScaledResolution sr = new ScaledResolution(mc);
        HudModule hovered = dragging != null ? dragging : at(mouseX, mouseY);
        for (HudModule h : enabled()) {
            NovoClient.draw(h, sr, true);
            int[] b = NovoClient.bounds(h, sr, true);
            boolean hot = h == hovered;
            if (hot) {
                Draw.rect(b[0], b[1], b[0] + b[2], b[1] + b[3], Draw.withAlpha(ACCENT, 0x22));
            }
            Draw.outline(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, hot ? ACCENT : 0x66FFFFFF);
            if (hot) {
                int labelY = b[1] > 12 ? b[1] - 11 : b[1] + b[3] + 2;
                Draw.text(h.def.name + "  " + Math.round(h.scale() * 100) + "%", b[0], labelY, ACCENT);
            }
        }

        String hint = "Drag to move · Scroll to resize · Right-click to reset";
        Draw.text(hint, (width - Draw.width(hint)) / 2f, 8, 0xFFBBBBBB);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        HudModule h = at(mouseX, mouseY);
        if (h == null) {
            return;
        }
        if (button == 0) {
            int[] b = NovoClient.bounds(h, new ScaledResolution(mc), true);
            dragging = h;
            grabX = mouseX - b[0];
            grabY = mouseY - b[1];
        } else if (button == 1) {
            h.resetPosition();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (dragging == null) {
            return;
        }
        int[] b = NovoClient.bounds(dragging, new ScaledResolution(mc), true);
        int x = mouseX - grabX;
        int y = mouseY - grabY;
        // Snap to the screen edges and center lines.
        snappedX = Math.abs(x + b[2] / 2 - width / 2) < SNAP;
        snappedY = Math.abs(y + b[3] / 2 - height / 2) < SNAP;
        if (snappedX) {
            x = width / 2 - b[2] / 2;
        } else if (x < SNAP) {
            x = 0;
        } else if (x + b[2] > width - SNAP) {
            x = width - b[2];
        }
        if (snappedY) {
            y = height / 2 - b[3] / 2;
        } else if (y < SNAP) {
            y = 0;
        } else if (y + b[3] > height - SNAP) {
            y = height - b[3];
        }
        float anchorX = dragging.def.centered ? x + b[2] / 2f : x;
        dragging.setPosition(anchorX / width, y / (float) height);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragging = null;
        snappedX = snappedY = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        HudModule h = at(x, y);
        if (h != null) {
            h.setScale(h.scale() + (wheel > 0 ? 0.1f : -0.1f));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(new GuiModules(this));
        } else if (button.id == 2) {
            mc.displayGuiScreen(new GuiResourcePacks(this));
        } else if (button.id == 1) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        NovoClient.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
