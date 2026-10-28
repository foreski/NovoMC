package com.novomc.modern.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.novomc.client.config.Catalog;
import com.novomc.modern.NovoModern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Novo's Mods screen on modern Minecraft, as on 1.8.9: category chips, a row
 * per mod with its switch, and settings that open under the row. Writes the
 * shared modules.json, so the launcher's Mod Library sees every change.
 */
public class NovoMods extends NovoPanel {
    private static final String[] COLORS = {"#FFFFFF", "#5B8CFF", "#B45BFF", "#22D3EE", "#34D399", "#FACC15", "#FB923C", "#F87171", "#F472B6", "#000000"};
    private static final int ROW = 30;
    private static final int SET = 20;

    private String category = "all";
    private String open;
    private Catalog.Def dragDef;
    private Catalog.Setting dragSetting;
    private int dragL;
    private int dragR;
    private Catalog.Def textDef;
    private Catalog.Setting textSetting;
    private String textValue;

    public NovoMods(Screen parent) {
        super(parent, "Mods");
    }

    private List<Catalog.Def> defs() {
        List<Catalog.Def> out = new ArrayList<Catalog.Def>();
        for (Catalog.Def d : Catalog.all()) {
            if (d.modern && ("all".equals(category) || category.equals(d.category))) {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int[] p = beginFrame(g, mouseX, mouseY);
        int l = p[0];
        int r = p[2];
        int on = 0;
        int total = 0;
        for (Catalog.Def d : Catalog.all()) {
            if (d.modern) {
                total++;
                on += NovoModern.config().enabled(d) ? 1 : 0;
            }
        }
        header(g, l, r, p[1] + 4, "Mods", total + " mods  ·  " + on + " on");

        List<String[]> chips = new ArrayList<String[]>();
        chips.add(new String[] {"all", "All"});
        for (Map.Entry<String, String> c : Catalog.categories().entrySet()) {
            for (Catalog.Def d : Catalog.all()) {
                if (d.modern && d.category.equals(c.getKey())) {
                    chips.add(new String[] {c.getKey(), c.getValue()});
                    break;
                }
            }
        }
        int hudW = minecraft.level != null ? 70 : 0;
        int top = chips(g, chips.toArray(new String[0][]), category, l, r - hudW - 6, p[1] + 60, id -> {
            category = id;
            scrollTarget = 0;
        }) + 8;
        if (hudW > 0) {
            int bx = r - hudW;
            int by = p[1] + 60;
            boolean hover = in(bx, by, r, by + 16);
            NovoUi.round(g, bx, by, r, by + 16, hover ? 0xFF6E9BFF : NovoUi.ACCENT);
            NovoUi.icon(g, "move", bx + 6, by + 4, 8, 0xFFFFFFFF);
            NovoUi.draw(g, NovoUi.text("Edit HUD"), bx + 18, by + 4, 0xFFFFFFFF);
            hit(bx, by, r, by + 16, () -> minecraft.gui.setScreen(new NovoHudEditor(this)));
        }

        int bottom = p[3] - PAD;
        List<Catalog.Def> list = defs();
        float content = 0;
        for (Catalog.Def d : list) {
            content += ROW + 4 + (d.id.equals(open) ? d.settings.size() * SET + 6 : 0);
        }
        float max = updateScroll(content, bottom - top);
        clip(g, l - 2, top, r + 8, bottom);
        int y = Math.round(top - scroll);
        for (Catalog.Def d : list) {
            y = row(g, d, l, r, y) + 4;
        }
        noClip(g);
        scrollbar(g, r, top, bottom, max);
        if (list.isEmpty()) {
            NovoUi.centered(g, NovoUi.text("No mods here yet"), (l + r) / 2, top + 20, NovoUi.MUTED);
        }
    }

    private int row(GuiGraphicsExtractor g, Catalog.Def d, int l, int r, int y) {
        boolean enabled = NovoModern.config().enabled(d);
        boolean expanded = d.id.equals(open);
        int h = ROW + (expanded ? d.settings.size() * SET + 6 : 0);
        boolean hover = in(l, y, r, y + ROW);
        NovoUi.round(g, l, y, r, y + h, hover ? 0x1AFFFFFF : 0x0DFFFFFF);
        if (enabled) {
            g.fill(l, y + 6, l + 2, y + ROW - 6, NovoUi.ACCENT);
        }
        NovoUi.draw(g, NovoUi.bold(d.name), l + 10, y + 6, NovoUi.TEXT);
        NovoUi.draw(g, NovoUi.trim(d.description, r - l - 90), l + 10, y + 17, NovoUi.MUTED);
        toggle(g, r - 34, y + 9, d.id, enabled);
        hit(r - 40, y, r, y + ROW, () -> {
            NovoModern.config().setEnabled(d.id, !enabled);
            commit();
        });
        if (!d.settings.isEmpty()) {
            NovoUi.icon(g, expanded ? "chevron-down" : "settings", r - 54, y + 11, 8, hover ? NovoUi.TEXT : NovoUi.MUTED);
            hit(l, y, r - 40, y + ROW, () -> open = expanded ? null : d.id);
        }
        if (expanded) {
            int sy = y + ROW;
            for (Catalog.Setting s : d.settings) {
                setting(g, d, s, l + 10, r - 10, sy);
                sy += SET;
            }
        }
        return y + h;
    }

    private void setting(GuiGraphicsExtractor g, Catalog.Def d, Catalog.Setting s, int l, int r, int y) {
        JsonElement v = NovoModern.config().setting(d, s.key);
        NovoUi.draw(g, NovoUi.text(s.label), l, y + 6, 0xFFC9D1E0);
        int cy = y + 3;
        switch (s.type) {
            case "toggle" -> {
                boolean b = v != null && v.getAsBoolean();
                toggle(g, r - 24, cy + 1, d.id + "." + s.key, b);
                hit(r - 30, y, r, y + SET, () -> set(d, s, new JsonPrimitive(!b)));
            }
            case "number" -> {
                float f = v != null ? v.getAsFloat() : s.min;
                int sl = r - 130;
                int sr = r - 34;
                float t = s.max > s.min ? (f - s.min) / (s.max - s.min) : 0;
                g.fill(sl, cy + 6, sr, cy + 8, 0xFF2A3142);
                int kx = sl + Math.round((sr - sl) * t);
                g.fillGradient(sl, cy + 6, kx, cy + 8, NovoUi.ACCENT, NovoUi.PURPLE);
                NovoUi.round(g, kx - 3, cy + 3, kx + 3, cy + 11, 0xFFFFFFFF);
                String label = s.step >= 1 ? String.valueOf(Math.round(f)) : String.format("%.2f", f);
                Component lbl = NovoUi.text(label);
                NovoUi.draw(g, lbl, r - NovoUi.width(lbl), y + 6, NovoUi.TEXT);
                hit(sl - 4, y, sr + 4, y + SET, () -> {
                    dragDef = d;
                    dragSetting = s;
                    dragL = sl;
                    dragR = sr;
                    drag(mouseX);
                });
            }
            case "choice" -> {
                String cur = v != null ? v.getAsString() : "";
                String label = cur;
                int idx = 0;
                for (int i = 0; i < s.options.size(); i++) {
                    if (s.options.get(i)[0].equals(cur)) {
                        label = s.options.get(i)[1];
                        idx = i;
                    }
                }
                pill(g, label, r, cy);
                String next = s.options.isEmpty() ? cur : s.options.get((idx + 1) % s.options.size())[0];
                hit(r - 110, y, r, y + SET, () -> set(d, s, new JsonPrimitive(next)));
            }
            case "color" -> {
                String cur = v != null ? v.getAsString().toUpperCase() : "#FFFFFF";
                int argb;
                try {
                    argb = 0xFF000000 | Integer.parseInt(cur.replace("#", ""), 16);
                } catch (NumberFormatException e) {
                    argb = 0xFFFFFFFF;
                }
                int idx = -1;
                for (int i = 0; i < COLORS.length; i++) {
                    if (COLORS[i].equals(cur)) {
                        idx = i;
                    }
                }
                Component lbl = NovoUi.text(cur);
                NovoUi.draw(g, lbl, r - 18 - NovoUi.width(lbl), y + 6, NovoUi.MUTED);
                NovoUi.round(g, r - 12, cy + 1, r, cy + 13, argb);
                NovoUi.roundOutline(g, r - 12, cy + 1, r, cy + 13, 0x55FFFFFF);
                String next = COLORS[(idx + 1) % COLORS.length];
                hit(r - 70, y, r, y + SET, () -> set(d, s, new JsonPrimitive(next)));
            }
            case "text" -> {
                boolean editing = d == textDef && s == textSetting;
                String cur = editing ? textValue + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : v != null ? v.getAsString() : "";
                int fl = r - 130;
                NovoUi.field(g, fl, cy, r - fl, 14, editing);
                NovoUi.draw(g, NovoUi.text(cur), fl + 4, cy + 3, NovoUi.TEXT);
                hit(fl, y, r, y + SET, () -> {
                    textDef = d;
                    textSetting = s;
                    JsonElement e = NovoModern.config().setting(d, s.key);
                    textValue = e != null ? e.getAsString() : "";
                });
            }
            default -> {}
        }
    }

    private void pill(GuiGraphicsExtractor g, String label, int r, int y) {
        Component c = NovoUi.text(label);
        int w = NovoUi.width(c) + 20;
        boolean hover = in(r - w, y, r, y + 14);
        NovoUi.round(g, r - w, y, r, y + 14, hover ? 0x335B8CFF : 0x1F5B8CFF);
        NovoUi.draw(g, c, r - w + 7, y + 3, NovoUi.TEXT);
        NovoUi.icon(g, "arrow-up-down", r - 11, y + 3, 8, NovoUi.MUTED);
    }

    private void drag(double x) {
        float t = (float) Math.max(0, Math.min(1, (x - dragL) / (double) (dragR - dragL)));
        float v = dragSetting.min + (dragSetting.max - dragSetting.min) * t;
        if (dragSetting.step > 0) {
            v = Math.round(v / dragSetting.step) * dragSetting.step;
        }
        NovoModern.config().setSetting(dragDef.id, dragSetting.key, new JsonPrimitive(v));
    }

    private void set(Catalog.Def d, Catalog.Setting s, JsonElement v) {
        finishText();
        NovoModern.config().setSetting(d.id, s.key, v);
        commit();
    }

    private void commit() {
        NovoModern.config().save();
        NovoModern.apply();
    }

    private void finishText() {
        if (textDef != null) {
            Catalog.Def d = textDef;
            textDef = null;
            NovoModern.config().setSetting(d.id, textSetting.key, new JsonPrimitive(textValue));
            commit();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        finishText();
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragDef != null) {
            drag(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragDef != null) {
            dragDef = null;
            commit();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (textDef != null) {
            switch (event.key()) {
                case InputConstants.KEY_BACKSPACE -> {
                    if (!textValue.isEmpty()) {
                        textValue = textValue.substring(0, textValue.length() - 1);
                    }
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_ESCAPE -> finishText();
                default -> {}
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (textDef != null) {
            if (textValue.length() < textSetting.maxLength && event.codepoint() >= 32) {
                textValue += Character.toString(event.codepoint());
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        finishText();
        super.removed();
    }
}
