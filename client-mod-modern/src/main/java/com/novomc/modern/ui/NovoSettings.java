package com.novomc.modern.ui;

import com.novomc.client.config.Catalog;
import com.novomc.modern.NovoModern;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Novo's settings screens on modern Minecraft (Options, Video Settings), as on
 * 1.8.9: a glass panel with section chips and a scrolling list of rows. Game
 * options use vanilla's own widgets (themed, value only), so every option
 * type behaves exactly as in the vanilla screens; links open other screens.
 */
public abstract class NovoSettings extends NovoPanel {
    private static final int ROW = 28;
    private static final int CONTROL_W = 150;

    /** One row: a vanilla option widget, a link or a Novo mod switch. */
    protected record Row(String label, String hint, OptionInstance<?> option, Supplier<Screen> link, String module) {}

    private final List<Row> rows = new ArrayList<Row>();
    private final List<AbstractWidget> controls = new ArrayList<AbstractWidget>();
    private String section;

    protected NovoSettings(Screen parent, String title) {
        super(parent, title);
    }

    /** {id, label} per section chip. */
    protected abstract String[][] sections();

    protected abstract void rows(String section, List<Row> out);

    protected abstract String subtitle();

    protected static Row option(OptionInstance<?> option, String label, String hint) {
        return new Row(label, hint, option, null, null);
    }

    protected static Row link(String label, String hint, Supplier<Screen> screen) {
        return new Row(label, hint, null, screen, null);
    }

    protected static Row module(String id, String label, String hint) {
        return new Row(label, hint, null, null, id);
    }

    @Override
    protected void init() {
        if (section == null) {
            section = sections()[0][0];
        }
        clearWidgets();
        rows.clear();
        controls.clear();
        rows(section, rows);
        for (Row r : rows) {
            AbstractWidget w = null;
            if (r.option() != null) {
                w = r.option().createButton(minecraft.options, 0, 0, CONTROL_W);
                NovoUi.VALUE_ONLY.add(w);
                addWidget(w);
            }
            controls.add(w);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int[] p = beginFrame(g, mouseX, mouseY);
        int l = p[0];
        int r = p[2];
        header(g, l, r, p[1] + 4, title.getString(), subtitle());
        int top = chips(g, sections(), section, l, r, p[1] + 60, id -> {
            section = id;
            scroll = scrollTarget = 0;
            rebuildWidgets();
        }) + 8;
        int bottom = p[3] - PAD;
        float max = updateScroll(rows.size() * (ROW + 4f), bottom - top);
        clip(g, l - 2, top, r + 8, bottom);
        int y = Math.round(top - scroll);
        for (int i = 0; i < rows.size(); i++) {
            row(g, rows.get(i), controls.get(i), l, r, y, top, bottom, partialTick);
            y += ROW + 4;
        }
        noClip(g);
        scrollbar(g, r, top, bottom, max);
    }

    private void row(GuiGraphicsExtractor g, Row row, AbstractWidget w, int l, int r, int y, int top, int bottom, float pt) {
        boolean shown = y + ROW > top && y < bottom;
        if (w != null) {
            w.visible = shown;
        }
        if (!shown) {
            return;
        }
        boolean hover = in(l, y, r, y + ROW);
        NovoUi.round(g, l, y, r, y + ROW, hover ? 0x1AFFFFFF : 0x0DFFFFFF);
        int ty = row.hint() == null ? y + 10 : y + 5;
        NovoUi.draw(g, NovoUi.bold(row.label()), l + 10, ty, NovoUi.TEXT);
        if (row.hint() != null) {
            NovoUi.draw(g, NovoUi.text(row.hint()), l + 10, y + 16, NovoUi.MUTED);
        }
        if (w != null) {
            w.setPosition(r - CONTROL_W - 4, y + (ROW - w.getHeight()) / 2);
            w.extractRenderState(g, mouseX, mouseY, pt);
        } else if (row.link() != null) {
            Component open = NovoUi.text("Open  ›");
            NovoUi.draw(g, open, r - 10 - NovoUi.width(open), y + 10, hover ? NovoUi.ACCENT : NovoUi.MUTED);
            hit(l, y, r, y + ROW, () -> minecraft.gui.setScreen(row.link().get()));
        } else if (row.module() != null) {
            Catalog.Def def = Catalog.get(row.module());
            boolean on = NovoModern.config().enabled(def);
            toggle(g, r - 34, y + 8, def.id, on);
            hit(l, y, r, y + ROW, () -> {
                NovoModern.config().setEnabled(def.id, !on);
                NovoModern.config().save();
                NovoModern.apply();
            });
        }
    }

    @Override
    public void removed() {
        minecraft.options.save();
        super.removed();
    }
}
