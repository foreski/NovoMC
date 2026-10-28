package com.novomc.client.gui;

import com.novomc.client.NovoClient;
import com.novomc.client.config.Catalog;
import com.novomc.client.util.Draw;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * NovoMC's settings screens (Options, Video Settings): a glass panel with a
 * gradient title, section chips and a scrolling list of rows, each a switch,
 * a value that cycles on click, a slider or a link to another screen.
 */
public abstract class GuiNovoSettings extends NovoPanelScreen {
    private static final int ROW = 30;

    /** One setting row. */
    protected abstract static class Row {
        final String label;
        final String hint;

        protected Row(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        /** Text on the right; null for switches. */
        protected String value() {
            return null;
        }

        /** Switch state, or null when this row is not a switch. */
        protected Boolean on() {
            return null;
        }

        /** Slider position 0..1, or -1 when this row is not a slider. */
        protected float fraction() {
            return -1;
        }

        protected void click() {}

        protected void slide(float fraction) {}

        protected void release() {}
    }

    protected final GameSettings gs;
    private String section;
    private List<Row> rows = new ArrayList<Row>();
    private Row dragging;

    protected GuiNovoSettings(GuiScreen parent) {
        super(parent);
        gs = Minecraft.getMinecraft().gameSettings;
        section = sections()[0][0];
    }

    protected abstract String title();

    protected abstract String subtitle();

    /** {id, label} pairs for the section chips. */
    protected abstract String[][] sections();

    protected abstract List<Row> build(String section);

    @Override
    protected String tab() {
        return "settings";
    }

    @Override
    protected boolean searchFocused() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        rows = build(section);
        enter();
    }

    public void showSection(String id) {
        section = id;
        rows = build(id);
        scroll = scrollTarget = 0;
        enter();
    }

    /** Rows slide up into place one after another (each starts a little later). */
    private void enter() {
        for (int i = 0; i < rows.size(); i++) {
            anim.put("in" + i, -0.12f * i);
        }
    }

    @Override
    protected void chipSelected(String group, String id) {
        showSection(id);
    }

    // ---- Row factories ----

    /** The value part of vanilla's "Name: value" button text. */
    protected String valueOf(GameSettings.Options opt) {
        String s = gs.getKeyBinding(opt);
        int i = s.indexOf(": ");
        return i >= 0 ? s.substring(i + 2) : s;
    }

    /** A vanilla option as a slider, switch or cycling value, whichever it is. */
    protected Row option(final GameSettings.Options opt, String label, String hint) {
        if (opt.getEnumFloat()) {
            return new Row(label, hint) {
                protected String value() {
                    return valueOf(opt);
                }

                protected float fraction() {
                    return opt.normalizeValue(gs.getOptionFloatValue(opt));
                }

                protected void slide(float f) {
                    float v = opt.denormalizeValue(Math.max(0, Math.min(1, f)));
                    if (v != gs.getOptionFloatValue(opt)) {
                        gs.setOptionFloatValue(opt, v);
                    }
                }
            };
        }
        if (opt.getEnumBoolean()) {
            return new Row(label, hint) {
                protected Boolean on() {
                    return gs.getOptionOrdinalValue(opt);
                }

                protected void click() {
                    gs.setOptionValue(opt, 1);
                }
            };
        }
        return new Row(label, hint) {
            protected String value() {
                return valueOf(opt);
            }

            protected void click() {
                gs.setOptionValue(opt, 1);
            }
        };
    }

    /** A NovoMC module's on/off switch. */
    protected Row module(final String id, String label, String hint) {
        return new Row(label, hint) {
            protected Boolean on() {
                return NovoClient.config().enabled(Catalog.get(id));
            }

            protected void click() {
                setModule(id, !on());
            }
        };
    }

    /** A row that opens another screen. */
    protected Row link(String label, String hint, final Runnable open) {
        return new Row(label, hint) {
            protected String value() {
                return "Open";
            }

            protected void click() {
                open.run();
            }
        };
    }

    protected static void setModule(String id, boolean on) {
        NovoClient.config().setEnabled(id, on);
        NovoClient.apply();
        NovoClient.save();
    }

    // ---- Drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int[] p = beginFrame(mouseX, mouseY);
        int l = p[0], top = p[1], r = p[2], bottom = p[3];

        Draw.spaced("NOVO CLIENT", l, top + 10, CYAN, 1.2f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(l, top + 22, 0);
        GlStateManager.scale(2, 2, 1);
        String title = title();
        float x = 0;
        for (int i = 0; i < title.length(); i++) {
            String c = String.valueOf(title.charAt(i));
            Draw.text(c, x, 0, Draw.blend(0xFFA9C2FF, 0xFFD6A4FF, i / (float) Math.max(1, title.length() - 1)));
            x += Draw.width(c);
        }
        GlStateManager.popMatrix();
        Draw.text(Draw.trim(subtitle(), r - l - 24), l, top + 44, MUTED);

        int cs = 16;
        int cx = r - cs;
        int cy = top + 9;
        boolean closeHover = in(cx, cy, cx + cs, cy + cs);
        Draw.round(cx, cy, cx + cs, cy + cs, 4, closeHover ? 0x33FFFFFF : 0x14FFFFFF);
        Draw.icon(CLOSE, cx + 4, cy + 4, 8, closeHover ? TEXT : MUTED);
        hit(cx, cy, cx + cs, cy + cs, this::back);

        String[][] sections = sections();
        int listTop = sections.length > 1
                ? drawChips("section", java.util.Arrays.asList(sections), section, l, r, top + 58) + 8 : top + 60;
        int listBottom = bottom - 8;

        updateDrag(r);
        float max = updateScroll(rows.size() * ROW, listBottom - listTop);
        clip(l - 2, listTop, r + 2, listBottom);
        float y = listTop - scroll;
        for (int i = 0; i < rows.size(); i++) {
            float in = Math.max(0, Math.min(1, ease("in" + i, 1.2f)));
            drawRow(rows.get(i), l, r, y + (1 - in) * (1 - in) * 10);
            y += ROW;
        }
        noClip();
        drawScrollbar(r, listTop, listBottom, max);
    }

    private void drawRow(final Row row, int l, int r, float y) {
        float b = y + ROW - 4;
        float hover = ease("h" + row.label, in(l, y, r, b) ? 1 : 0);
        Draw.round(l, y, r, b, 5, Draw.blend(0x0AFFFFFF, 0x16FFFFFF, hover));
        float ty = row.hint != null ? y + 4 : y + 9;
        Draw.text(row.label, l + 8, ty, TEXT);
        if (row.hint != null) {
            Draw.text(Draw.trim(row.hint, r - l - 190), l + 8, ty + 11, MUTED);
        }

        float cy = y + (ROW - 4) / 2f;
        Boolean on = row.on();
        float frac = row.fraction();
        if (on != null) {
            toggle(r - 32, cy - 6, 24, 12, ease(row.label, on ? 1 : 0));
            hit(l, y, r, b, row::click);
        } else if (frac >= 0) {
            float tl = r - 130;
            float tr = r - 10;
            String v = row.value();
            Draw.text(v, tl - 8 - Draw.width(v), cy - 4, 0xFFB9CBFF);
            Draw.round(tl, cy - 2, tr, cy + 2, 2, 0xFF2A3142);
            float kx = tl + (tr - tl) * Math.max(0, Math.min(1, frac));
            Draw.round(tl, cy - 2, kx, cy + 2, 2, ACCENT);
            Draw.round(kx - 4, cy - 4, kx + 4, cy + 4, 4, 0xFFFFFFFF);
            hit(tl - 4, y, tr + 6, b, () -> {
                dragging = row;
                updateDrag(r);
            });
        } else {
            String v = row.value();
            float w = Draw.width(v) + 22;
            float pl = r - 10 - w;
            boolean ph = in(pl, cy - 8, r - 10, cy + 8);
            Draw.round(pl, cy - 8, r - 10, cy + 8, 8, ph ? 0x2E5B8CFF : 0x14FFFFFF);
            Draw.roundOutline(pl, cy - 8, r - 10, cy + 8, 8, ph ? 0x995B8CFF : 0x26FFFFFF);
            Draw.text("‹", pl + 5, cy - 4, MUTED);
            Draw.text(v, pl + 11, cy - 4, TEXT);
            Draw.text("›", r - 18, cy - 4, MUTED);
            hit(l, y, r, b, row::click);
        }
    }

    /** Follows the mouse while a slider is held; releasing it calls the row's release. */
    private void updateDrag(int r) {
        if (dragging == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            dragging.release();
            dragging = null;
            return;
        }
        float tl = r - 130;
        float tr = r - 10;
        dragging.slide((mouseX - tl) / (tr - tl));
    }

    @Override
    protected void keyTyped(char c, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            back();
        }
    }

    @Override
    protected void back() {
        gs.saveOptions();
        mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
