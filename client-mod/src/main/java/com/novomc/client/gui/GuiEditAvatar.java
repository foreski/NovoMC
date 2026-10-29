package com.novomc.client.gui;

import java.util.List;

import com.novomc.client.util.Draw;
import net.minecraft.client.gui.GuiScreen;

/**
 * The wardrobe the Novo menu's Cosmetics tile opens: everything the account
 * owns, one tab per slot, each item as a small figure wearing it.
 *
 * Picking an item writes `config/novomc/cosmetics.json` right away, so the
 * player is wearing it as soon as this screen closes and the launcher saves it
 * to the account after the game exits. The preview on the left always shows the
 * outfit as it stands, so the tabs can be browsed without changing anything.
 */
public class GuiEditAvatar extends NovoPanelScreen {
    private static final int TILE_W = 64;
    private static final int TILE_H = 72;
    private static final int GAP = 6;
    /** Below this the preview and two columns of items stop fitting side by side. */
    private static final int NARROW = 300;
    /** Pixels per model unit inside a tile: wide enough for a wing span, tall enough for the figure. */
    private static final float TILE_UNIT = 1.4F;

    private final Cosmetics cosmetics;
    private String kind = Cosmetics.CAPE;

    public GuiEditAvatar(GuiScreen parent) {
        super(parent);
        this.cosmetics = Cosmetics.load();
    }

    @Override
    protected String tab() {
        return kind;
    }

    @Override
    protected String[][] headerTabs() {
        return new String[][] {
            {Cosmetics.CAPE, "Capes"}, {Cosmetics.WING, "Wings"}, {Cosmetics.HAT, "Hats"},
        };
    }

    @Override
    protected void headerTabSelected(String id) {
        kind = id;
        scrollTarget = 0;
    }

    /** No search box on this screen, so typing does nothing. */
    @Override
    protected boolean searchFocused() {
        return false;
    }

    /** Nothing sits left of the header's tabs, so the subtitle may use the room. */
    @Override
    protected int searchWidth(int l, int r) {
        return 0;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int[] p = beginFrame(mouseX, mouseY);
        int l = p[0];
        int top = p[1];
        int r = p[2];
        int bottom = p[3];
        drawHeader(l, r, top, "Edit Avatar", "Wear anything you own. Changes save when you exit the game.");

        float contentTop = top + 58;
        float contentBottom = bottom - PAD;
        if (cosmetics == null) {
            Draw.text("Sign in on the launcher to load your cosmetics.", l, contentTop, MUTED);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        if (r - l < NARROW) {
            // Too narrow for a side preview: the tiles carry the whole screen.
            drawCatalog(l, r, contentTop, contentBottom);
        } else {
            float cardW = Math.max(112, Math.min(168, (r - l) * 0.3f));
            drawPreview(l, contentTop, l + cardW, contentBottom);
            drawCatalog(l + cardW + PAD, r, contentTop, contentBottom);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The big figure on the left, with the three worn names underneath it. */
    private void drawPreview(float l, float top, float r, float bottom) {
        Draw.round(l, top, r, bottom, 6, 0x99080C14);
        Draw.roundOutline(l, top, r, bottom, 6, 0x22FFFFFF);
        Draw.text("WEARING", l + 8, top + 6, MUTED);

        float namesH = Cosmetics.KINDS.length * 11 + 4;
        float figureTop = top + 20;
        float figureBottom = bottom - namesH - 6;
        float unit = Math.min((r - l - 16) / (Avatar.WING_HALF * 2), (figureBottom - figureTop) / Avatar.FIGURE_H);
        unit = Math.max(1.0F, Math.min(4.0F, unit));
        float neck = figureTop + Avatar.ABOVE_NECK * unit;
        Avatar.draw((l + r) / 2f, neck, unit, 18.0F, 0.0F,
                cosmetics.worn(Cosmetics.CAPE), cosmetics.worn(Cosmetics.WING), cosmetics.worn(Cosmetics.HAT));

        for (int i = 0; i < Cosmetics.KINDS.length; i++) {
            String id = cosmetics.worn(Cosmetics.KINDS[i]);
            float ty = bottom - namesH + i * 11;
            Draw.text(Cosmetics.label(Cosmetics.KINDS[i]), l + 8, ty, MUTED);
            Draw.text(Draw.trim(cosmetics.nameOf(Cosmetics.KINDS[i], id), Math.round(r - l) - 60), l + 48, ty,
                    id == null ? 0xFF7C879C : TEXT);
        }
    }

    /** The scrollable grid of owned items for the active tab, "None" first. */
    private void drawCatalog(float l, float r, float top, float bottom) {
        List<Cosmetics.Entry> entries = cosmetics.owned(kind);
        int count = entries.size() + 1;
        int columns = Math.max(2, (int) ((r - l + GAP) / (TILE_W + GAP)));
        int rows = (count + columns - 1) / columns;
        float content = rows * (TILE_H + GAP);
        float max = updateScroll(content, bottom - top);

        clip(l, top, r, bottom);
        for (int i = 0; i < count; i++) {
            float tx = l + (i % columns) * (TILE_W + GAP);
            float ty = top + (i / columns) * (TILE_H + GAP) - scroll;
            if (ty + TILE_H < top || ty > bottom) {
                continue;
            }
            drawTile(tx, ty, i == 0 ? null : entries.get(i - 1));
        }
        if (entries.isEmpty()) {
            Draw.text("Nothing owned in this slot yet.", l, top + TILE_H + GAP + 4, MUTED);
        }
        noClip();
        drawScrollbar(Math.round(r), Math.round(top), Math.round(bottom), max);
    }

    /** One item: a bare figure wearing it, its name, and a ring when it is worn. */
    private void drawTile(float tx, float ty, final Cosmetics.Entry entry) {
        final String id = entry == null ? null : entry.id;
        String worn = cosmetics.worn(kind);
        boolean equipped = id == null ? worn == null : id.equals(worn);
        boolean hover = in(tx, ty, tx + TILE_W, ty + TILE_H);
        Draw.round(tx, ty, tx + TILE_W, ty + TILE_H, 5,
                equipped ? 0x335B8CFF : hover ? 0x1FFFFFFF : 0x14FFFFFF);
        Draw.roundOutline(tx, ty, tx + TILE_W, ty + TILE_H, 5, equipped ? 0xCC5B8CFF : 0x22FFFFFF);

        Avatar.draw(tx + TILE_W / 2f, ty + 24, TILE_UNIT, 24.0F, 0.0F,
                Cosmetics.CAPE.equals(kind) ? id : null,
                Cosmetics.WING.equals(kind) ? id : null,
                Cosmetics.HAT.equals(kind) ? id : null);

        String name = entry == null ? "None" : entry.name;
        String shown = Draw.trim(name, TILE_W - 6);
        Draw.text(shown, tx + (TILE_W - Draw.width(shown)) / 2f, ty + TILE_H - 10,
                id == null ? MUTED : equipped ? 0xFFFFFFFF : TEXT);

        hit(tx, ty, tx + TILE_W, ty + TILE_H, new Runnable() {
            public void run() {
                cosmetics.wear(kind, id);
            }
        });
    }
}
