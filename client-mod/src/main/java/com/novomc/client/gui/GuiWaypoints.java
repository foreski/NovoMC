package com.novomc.client.gui;

import com.novomc.client.NovoClient;
import com.novomc.client.module.Module;
import com.novomc.client.modules.WaypointsModule;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

/** The waypoint list for the current server or world: add, show/hide, recolor and delete. */
public class GuiWaypoints extends NovoPanelScreen {
    private static final int ROW = 26;
    private static final ResourceLocation TRASH = icon("x");
    private static final ResourceLocation PIN = icon("move");

    public GuiWaypoints(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected String tab() {
        return "waypoints";
    }

    private static WaypointsModule module() {
        for (Module m : NovoClient.modules()) {
            if (m instanceof WaypointsModule) {
                return (WaypointsModule) m;
            }
        }
        throw new IllegalStateException("Waypoints module missing");
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        final WaypointsModule wm = module();
        int[] p = beginFrame(mouseX, mouseY);
        int l = p[0], top = p[1], r = p[2];
        List<WaypointsModule.Waypoint> all = wm.here();
        String where = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "this world";
        drawHeader(l, r, top, "Waypoints", all.size() + " saved on " + where + (wm.isEnabled() ? "" : " (Waypoints mod is off)"));
        drawSearch(r - searchWidth(l, r), top + 31, r, "Search waypoints");

        // "Add here" button left of the search box.
        float right = r - searchWidth(l, r) - 4;
        String add = "Add here";
        float bw = Draw.width(add) + 24;
        boolean hover = in(right - bw, top + 31, right, top + 47);
        Draw.round(right - bw, top + 31, right, top + 47, 8, hover ? 0xFF6C9BFF : ACCENT);
        Draw.icon(PIN, right - bw + 7, top + 35, 8, 0xFFFFFFFF);
        Draw.text(add, right - bw + 17, top + 35, 0xFFFFFFFF);
        hit(right - bw, top + 31, right, top + 47, new Runnable() {
            public void run() {
                if (mc.thePlayer != null) {
                    wm.addHere();
                }
            }
        });

        String q = search.toString().trim().toLowerCase();
        List<WaypointsModule.Waypoint> shown = new ArrayList<WaypointsModule.Waypoint>();
        for (WaypointsModule.Waypoint w : all) {
            if (q.isEmpty() || w.name.toLowerCase().contains(q)) {
                shown.add(w);
            }
        }
        int gridTop = top + 60;
        int bottom = p[3] - PAD;
        float max = updateScroll(shown.size() * (ROW + 4), bottom - gridTop);
        clip(l - 1, gridTop, r + 1, bottom);
        float y = gridTop - scroll;
        for (final WaypointsModule.Waypoint w : shown) {
            if (y + ROW >= gridTop && y <= bottom) {
                row(wm, w, l, y, r);
            }
            y += ROW + 4;
        }
        noClip();
        if (shown.isEmpty()) {
            String hint = all.isEmpty() ? "No waypoints yet. Press B in game (or Add here) to mark where you stand." : "No waypoints match.";
            Draw.text(hint, (l + r - Draw.width(hint)) / 2f, gridTop + 20, MUTED);
        }
        drawScrollbar(r, gridTop, bottom, max);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void row(final WaypointsModule wm, final WaypointsModule.Waypoint w, float l, float y, float r) {
        boolean hover = in(l, y, r, y + ROW);
        Draw.round(l, y, r, y + ROW, 6, hover ? 0xD01C2333 : 0xB0121722);
        Draw.roundOutline(l, y, r, y + ROW, 6, w.visible ? Draw.withAlpha(w.color, 0x88) : 0x1FFFFFFF);

        // Color dot: click to cycle through the palette.
        float cx = l + 8;
        Draw.round(cx, y + 7, cx + 12, y + 19, 6, w.visible ? w.color : Draw.withAlpha(w.color, 0x55));
        hit(cx - 2, y + 5, cx + 14, y + 21, new Runnable() {
            public void run() {
                int next = 0;
                for (int i = 0; i < WaypointsModule.PALETTE.length; i++) {
                    if (WaypointsModule.PALETTE[i] == w.color) {
                        next = (i + 1) % WaypointsModule.PALETTE.length;
                    }
                }
                w.color = WaypointsModule.PALETTE[next];
                wm.save();
            }
        });

        String dims = w.dim == -1 ? " · Nether" : w.dim == 1 ? " · End" : "";
        Draw.text(Draw.trim(w.name, (int) (r - l - 190)), l + 28, y + 4, w.visible ? TEXT : MUTED);
        String info = w.x + ", " + w.y + ", " + w.z + dims;
        if (mc.thePlayer != null && mc.thePlayer.dimension == w.dim) {
            double d = Math.sqrt(mc.thePlayer.getDistanceSq(w.x + 0.5, w.y, w.z + 0.5));
            info += " · " + Math.round(d) + "m away";
        }
        Draw.text(info, l + 28, y + 14, MUTED);

        float bx = r - 8;
        bx = button(bx, y + 7, "Delete", TRASH, new Runnable() {
            public void run() {
                wm.here().remove(w);
                wm.save();
            }
        });
        button(bx, y + 7, w.visible ? "Hide" : "Show", null, new Runnable() {
            public void run() {
                w.visible = !w.visible;
                wm.save();
            }
        });
    }
}
