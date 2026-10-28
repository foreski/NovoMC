package com.novomc.modern.ui;

import com.novomc.modern.More.Waypoints;
import com.novomc.modern.More.Waypoints.Waypoint;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The waypoint list (M), as on 1.8.9: colour dot to cycle, Hide/Show, Delete and Add here. */
public class NovoWaypoints extends NovoPanel {
    private static final int ROW = 28;

    public NovoWaypoints(Screen parent) {
        super(parent, "Waypoints");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Waypoints wm = Waypoints.get();
        int[] p = beginFrame(g, mouseX, mouseY);
        int l = p[0];
        int r = p[2];
        boolean inWorld = minecraft.level != null && minecraft.player != null;
        List<Waypoint> all = inWorld ? wm.here() : List.of();
        String where = minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "this world";
        header(g, l, r, p[1] + 4, "Waypoints", all.size() + " saved on " + where + (wm.isEnabled() ? "" : "  ·  Waypoints mod is off"));

        int top = p[1] + 60;
        if (inWorld) {
            Component add = NovoUi.text("Add here");
            int bw = NovoUi.width(add) + 16;
            boolean hover = in(r - bw, top, r, top + 16);
            NovoUi.round(g, r - bw, top, r, top + 16, hover ? 0xFF6E9BFF : NovoUi.ACCENT);
            NovoUi.draw(g, add, r - bw + 8, top + 4, 0xFFFFFFFF);
            hit(r - bw, top, r, top + 16, wm::addHere);
        }
        int listTop = top + 22;
        int bottom = p[3] - PAD;
        float max = updateScroll(all.size() * (ROW + 4f), bottom - listTop);
        clip(g, l - 2, listTop, r + 8, bottom);
        int y = Math.round(listTop - scroll);
        for (Waypoint w : List.copyOf(all)) {
            row(g, wm, w, l, r, y);
            y += ROW + 4;
        }
        noClip(g);
        scrollbar(g, r, listTop, bottom, max);
        if (all.isEmpty()) {
            String hint = inWorld ? "No waypoints yet. Press " + Waypoints.ADD_KEY.getTranslatedKeyMessage().getString()
                    + " in game (or Add here) to mark where you stand." : "Join a world to see its waypoints.";
            NovoUi.centered(g, NovoUi.text(hint), (l + r) / 2, listTop + 20, NovoUi.MUTED);
        }
    }

    private void row(GuiGraphicsExtractor g, Waypoints wm, Waypoint w, int l, int r, int y) {
        boolean hover = in(l, y, r, y + ROW);
        NovoUi.round(g, l, y, r, y + ROW, hover ? 0x1AFFFFFF : 0x0DFFFFFF);
        // Colour dot: click to cycle through the palette.
        NovoUi.round(g, l + 8, y + 8, l + 20, y + 20, w.visible ? w.color : NovoUi.withAlpha(w.color, 0x55));
        hit(l + 6, y + 6, l + 22, y + 22, () -> {
            int next = 0;
            for (int i = 0; i < Waypoints.PALETTE.length; i++) {
                if (Waypoints.PALETTE[i] == w.color) {
                    next = (i + 1) % Waypoints.PALETTE.length;
                }
            }
            w.color = Waypoints.PALETTE[next];
            wm.save();
        });
        NovoUi.draw(g, NovoUi.bold(w.name), l + 28, y + 5, w.visible ? NovoUi.TEXT : NovoUi.MUTED);
        String info = w.x + ", " + w.y + ", " + w.z + (w.dim == -1 ? "  ·  Nether" : w.dim == 1 ? "  ·  End" : "");
        if (minecraft.player != null && wm.dimension() == w.dim) {
            double d = Math.sqrt(minecraft.player.distanceToSqr(w.x + 0.5, w.y, w.z + 0.5));
            info += "  ·  " + Math.round(d) + "m away";
        }
        NovoUi.draw(g, NovoUi.text(info), l + 28, y + 16, NovoUi.MUTED);
        int bx = button(g, r - 8, y + 6, "Delete", () -> {
            wm.here().remove(w);
            wm.save();
        });
        button(g, bx, y + 6, w.visible ? "Hide" : "Show", () -> {
            w.visible = !w.visible;
            wm.save();
        });
    }

    /** A small button ending at {@code right}; returns its left edge minus a gap. */
    private int button(GuiGraphicsExtractor g, int right, int y, String label, Runnable action) {
        Component c = NovoUi.text(label);
        int w = NovoUi.width(c) + 14;
        boolean hover = in(right - w, y, right, y + 16);
        NovoUi.round(g, right - w, y, right, y + 16, hover ? 0x33FFFFFF : 0x14FFFFFF);
        NovoUi.draw(g, c, right - w + 7, y + 4, hover ? NovoUi.TEXT : NovoUi.MUTED);
        hit(right - w, y, right, y + 16, action);
        return right - w - 4;
    }
}
