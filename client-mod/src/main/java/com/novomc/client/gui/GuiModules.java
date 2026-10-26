package com.novomc.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.novomc.client.NovoClient;
import com.novomc.client.config.Catalog;
import com.novomc.client.module.HudModule;
import com.novomc.client.module.Module;
import com.novomc.client.util.Draw;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/**
 * In-game Mod Library, laid out like the launcher's: category chips, search,
 * and a card per module with its description, an on/off switch and, when
 * expanded, every setting from catalog.json. Changes save immediately, so the
 * launcher sees them too.
 */
public class GuiModules extends NovoPanelScreen {
    private static final int HEADER = 30;
    private static final int ROW = 18;
    private static final int PALETTE_ROW = 16;
    private static final String[] PALETTE = {
        "#FFFFFF", "#AAAAAA", "#555555", "#000000", "#FF5555", "#FFAA00",
        "#FFFF55", "#55FF55", "#55FFFF", "#5B8CFF", "#B45BFF", "#FF55FF"
    };

    private static final ResourceLocation CHEVRON = icon("chevron-down");
    private static final ResourceLocation MOVE = icon("move");
    private static final ResourceLocation RESET = icon("rotate-ccw");
    private static final Map<String, ResourceLocation> CATEGORY_ICON = new HashMap<String, ResourceLocation>();

    static {
        CATEGORY_ICON.put("hud", icon("layout-dashboard"));
        CATEGORY_ICON.put("visual", SPARKLES);
        CATEGORY_ICON.put("qol", SPARKLES);
        CATEGORY_ICON.put("performance", icon("gauge"));
    }

    private final Set<String> open = new HashSet<String>();
    private String category = "all";
    /** "module/setting" of the focused text or hex field; null types into search. */
    private String focus;
    private boolean focusHex;
    private final StringBuilder edit = new StringBuilder();
    /** "module/setting" whose color palette is showing. */
    private String palette;
    private String paletteBeforeClick;

    private Module dragModule;
    private Catalog.Setting dragSetting;
    private float dragLeft;
    private float dragRight;
    private long savedAt;

    public GuiModules(GuiScreen parent) {
        super(parent);
    }

    /** Opens with a module's settings expanded (used by the dev autotest). */
    public GuiModules expand(String id) {
        open.add(id);
        anim.put("open/" + id, 1f);
        return this;
    }

    @Override
    protected String tab() {
        return "mods";
    }

    @Override
    protected boolean searchFocused() {
        return focus == null;
    }

    // ---- Drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int[] p = beginFrame(mouseX, mouseY);
        int l = p[0];
        int top = p[1];
        int r = p[2];
        int on = 0;
        for (Module m : NovoClient.modules()) {
            if (m.isEnabled()) {
                on++;
            }
        }
        String subtitle = on + " of " + NovoClient.modules().size() + " mods on. Changes apply instantly.";
        headerReserve = mc.theWorld != null ? Draw.width("Edit HUD Layout") + 30 : 0;
        float tabsLeft = drawHeader(l, r, top, "Mod Library", subtitle);
        drawSaved(tabsLeft - 6, top + 9);
        drawSearch(r - searchWidth(l, r), top + 31, r, "Search mods");
        if (mc.theWorld != null) {
            drawEditHud(r - searchWidth(l, r) - 4, top + 31);
        }

        List<String[]> chips = new ArrayList<String[]>();
        chips.add(new String[] {"all", "All"});
        for (Map.Entry<String, String> c : Catalog.categories().entrySet()) {
            chips.add(new String[] {c.getKey(), c.getValue()});
        }
        int toolbarBottom = drawChips("category", chips, category, CATEGORY_ICON, l, r, top + 58);
        drawGrid(l, r, toolbarBottom + 8, p[3] - PAD);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** "Edit HUD Layout": opens the drag-and-drop HUD editor. Ends at `right`. */
    private void drawEditHud(float right, float y) {
        String text = "Edit HUD Layout";
        float w = Draw.width(text) + 26;
        float l = right - w;
        boolean hover = in(l, y, right, y + 16);
        Draw.round(l, y, right, y + 16, 8, hover ? 0xFF6C9BFF : ACCENT);
        Draw.icon(MOVE, l + 7, y + 4, 8, 0xFFFFFFFF);
        Draw.text(text, l + 18, y + 4, 0xFFFFFFFF);
        hit(l, y, right, y + 16, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiHudEditor());
            }
        });
    }

    /** The green "Saved" flash, ending at `right`. */
    private void drawSaved(float right, float y) {
        float saved = ease("saved", System.currentTimeMillis() - savedAt < 1400 ? 1 : 0);
        if (saved > 0.02f) {
            String text = "Saved";
            int sw = Draw.width(text) + 14;
            float sx = right - sw;
            float sy = y + (1 - saved) * 4;
            Draw.round(sx, sy, sx + sw, sy + 16, 8, Draw.withAlpha(GREEN, Math.round(saved * 40)));
            Draw.text(text, sx + 7, sy + 4, Draw.withAlpha(GREEN, Math.max(4, Math.round(saved * 255))));
        }
    }

    @Override
    protected void chipSelected(String group, String id) {
        category = id;
        scrollTarget = 0;
    }

    private void drawGrid(int l, int r, int top, int bottom) {
        List<Module> shown = new ArrayList<Module>();
        String q = search.toString().trim().toLowerCase(Locale.ROOT);
        for (Module m : NovoClient.modules()) {
            boolean inCategory = "all".equals(category) || m.def.category.equals(category);
            boolean matches = q.isEmpty() || (m.def.name + " " + m.def.description).toLowerCase(Locale.ROOT).contains(q);
            if (inCategory && matches) {
                shown.add(m);
            }
        }

        // Two columns only when cards stay wide enough to read the descriptions.
        int cols = r - l >= 460 ? 2 : 1;
        int gap = 6;
        float colW = (r - l - gap * (cols - 1)) / (float) cols;
        float[] colY = new float[cols];

        // Heights first, so scrolling can be clamped before drawing.
        float[] heights = new float[shown.size()];
        for (int i = 0; i < shown.size(); i++) {
            Module m = shown.get(i);
            float t = ease("open/" + m.id(), open.contains(m.id()) ? 1 : 0);
            heights[i] = HEADER + t * expandedHeight(m);
            colY[i % cols] += heights[i] + gap;
        }
        float content = 0;
        for (float c : colY) {
            content = Math.max(content, c - gap);
        }
        float max = updateScroll(content, bottom - top);

        clip(l - 1, top, r + 1, bottom);
        for (int c = 0; c < cols; c++) {
            colY[c] = top - scroll;
        }
        for (int i = 0; i < shown.size(); i++) {
            int c = i % cols;
            float x = l + c * (colW + gap);
            float y = colY[c];
            if (y + heights[i] >= top && y <= bottom) {
                drawCard(shown.get(i), x, y, colW, heights[i], l - 1, top, r + 1, bottom);
            }
            colY[c] += heights[i] + gap;
        }
        noClip();

        if (shown.isEmpty()) {
            String text = "No mods match.";
            Draw.text(text, (l + r - Draw.width(text)) / 2f, top + 30, MUTED);
        }
        drawScrollbar(r, top, bottom, max);
    }

    private float expandedHeight(Module m) {
        float h = 0;
        for (Catalog.Setting s : m.def.settings) {
            h += rowHeight(m, s);
        }
        if (m.def.hud) {
            h += 20;
        }
        return h == 0 ? 0 : h + 8;
    }

    private float rowHeight(Module m, Catalog.Setting s) {
        return ROW + (key(m, s).equals(palette) ? PALETTE_ROW : 0);
    }

    private boolean expandable(Module m) {
        return !m.def.settings.isEmpty() || m.def.hud;
    }

    private void drawCard(final Module m, float x, float y, float w, float h, float gl, float gt, float gr, float gb) {
        boolean on = m.isEnabled();
        boolean hover = in(x, y, x + w, y + h);
        float hv = ease("hover/" + m.id(), hover ? 1 : 0);
        float sw = ease("switch/" + m.id(), on ? 1 : 0);

        clip(gl, gt, gr, gb);
        int fill = Draw.blend(0xB0121722, 0xC0182340, sw);
        Draw.round(x, y, x + w, y + h, 6, Draw.blend(fill, 0xD01C2333, hv * 0.5f));
        Draw.roundOutline(x, y, x + w, y + h, 6, Draw.blend(Draw.blend(0x1FFFFFFF, 0x775B8CFF, sw), 0x40FFFFFF, hv * (1 - sw) * 0.6f));
        clip(Math.max(gl, x), Math.max(gt, y), Math.min(gr, x + w), Math.min(gb, y + h));

        // Header: category icon, name, description, chevron and switch.
        float bx = x + 6;
        float by = y + 5;
        Draw.round(bx, by, bx + 20, by + 20, 5, Draw.blend(0xFF1E2433, 0xFF4A5FD0, sw));
        if (sw > 0) {
            Draw.round(bx + 10, by, bx + 20, by + 20, 5, Draw.withAlpha(PURPLE, Math.round(sw * 70)));
        }
        Draw.roundOutline(bx, by, bx + 20, by + 20, 5, 0x1AFFFFFF);
        ResourceLocation icon = CATEGORY_ICON.get(m.def.category);
        Draw.icon(icon != null ? icon : SPARKLES, bx + 5, by + 5, 10, Draw.blend(MUTED, 0xFFFFFFFF, sw));

        boolean canExpand = expandable(m);
        float switchX = x + w - 26;
        float textW = switchX - (x + 32) - (canExpand ? 16 : 6);
        Draw.text(Draw.trim(m.def.name, (int) textW), x + 32, y + 6, TEXT);
        Draw.text(Draw.trim(m.def.description, (int) textW), x + 32, y + 17, MUTED);

        hit(x, y, x + w, y + HEADER, new Runnable() {
            public void run() {
                if (expandable(m)) {
                    if (!open.remove(m.id())) {
                        open.add(m.id());
                    }
                } else {
                    setEnabled(m, !m.isEnabled());
                }
            }
        });
        if (canExpand) {
            Float opened = anim.get("open/" + m.id());
            float t = opened != null ? opened : 0;
            GlStateManager.pushMatrix();
            GlStateManager.translate(switchX - 10, y + 15, 0);
            GlStateManager.rotate(180 * t, 0, 0, 1);
            Draw.icon(CHEVRON, -4, -4, 8, hover ? TEXT : MUTED);
            GlStateManager.popMatrix();
        }
        toggle(switchX, y + 10, 20, 10, sw);
        hit(switchX - 2, y + 6, switchX + 22, y + 24, new Runnable() {
            public void run() {
                setEnabled(m, !m.isEnabled());
            }
        });

        // Settings, revealed as the card expands.
        if (h > HEADER + 1) {
            Draw.rect(Math.round(x + 8), Math.round(y + HEADER), Math.round(x + w - 8), Math.round(y + HEADER) + 1, 0x14FFFFFF);
            float ry = y + HEADER + 4;
            for (Catalog.Setting s : m.def.settings) {
                drawSetting(m, s, x + 10, ry, x + w - 10);
                ry += rowHeight(m, s);
            }
            if (m.def.hud) {
                drawHudRow(m, x + 8, ry + 2, x + w - 8);
            }
        }
        clip(gl, gt, gr, gb);
    }

    private void drawSetting(final Module m, final Catalog.Setting s, float l, float y, final float r) {
        JsonElement value = NovoClient.config().setting(m.def, s.key);
        final String key = key(m, s);
        float cy = y + ROW / 2f;
        float controlW = 0;

        if ("toggle".equals(s.type)) {
            boolean on = value != null && value.getAsBoolean();
            float t = ease("set/" + key, on ? 1 : 0);
            toggle(r - 16, cy - 4.5f, 16, 9, t);
            controlW = 16;
            final boolean next = !on;
            hit(l, y, r, y + ROW, new Runnable() {
                public void run() {
                    set(m, s, new JsonPrimitive(next));
                }
            });
        } else if ("color".equals(s.type)) {
            String hex = value != null ? value.getAsString().toUpperCase(Locale.ROOT) : "#FFFFFF";
            float sx = r - 12;
            Draw.round(sx, cy - 6, r, cy + 6, 3, Draw.parseColor(hex, 0xFFFFFFFF));
            Draw.roundOutline(sx, cy - 6, r, cy + 6, 3, 0x40FFFFFF);
            hit(sx - 1, cy - 7, r + 1, cy + 7, new Runnable() {
                public void run() {
                    palette = key.equals(paletteBeforeClick) ? null : key;
                }
            });
            boolean editing = key.equals(focus) && focusHex;
            String shown = editing ? "#" + edit : hex;
            float hx = sx - 5 - Draw.width("#DDDDDD");
            if (editing) {
                Draw.round(hx - 3, cy - 6, sx - 3, cy + 6, 3, FIELD);
                Draw.roundOutline(hx - 3, cy - 6, sx - 3, cy + 6, 3, 0x995B8CFF);
            }
            Draw.text(shown, hx, cy - 4, editing ? TEXT : MUTED);
            caret(editing, hx + Draw.width(shown), cy - 5);
            final String current = hex;
            hit(hx - 3, cy - 6, sx - 3, cy + 6, new Runnable() {
                public void run() {
                    focus = key;
                    focusHex = true;
                    edit.setLength(0);
                    edit.append(current.substring(1));
                }
            });
            controlW = r - hx + 3;
            if (key.equals(palette)) {
                float px = r;
                float py = y + ROW;
                for (int i = PALETTE.length - 1; i >= 0; i--) {
                    final String c = PALETTE[i];
                    float x0 = px - 11;
                    boolean chosen = c.equalsIgnoreCase(hex);
                    Draw.round(x0, py, px, py + 11, 3, Draw.parseColor(c, 0xFFFFFFFF));
                    Draw.roundOutline(x0, py, px, py + 11, 3, chosen ? 0xFFFFFFFF : in(x0, py, px, py + 11) ? 0x99FFFFFF : 0x30FFFFFF);
                    hit(x0, py, px, py + 11, new Runnable() {
                        public void run() {
                            set(m, s, new JsonPrimitive(c));
                            palette = key;
                        }
                    });
                    px -= 14;
                }
            }
        } else if ("number".equals(s.type)) {
            float v = value != null ? value.getAsFloat() : s.min;
            String text = formatNumber(v, s.step);
            float valueW = Math.max(Draw.width(formatNumber(s.max, s.step)), 14);
            final float tl = r - valueW - 76;
            final float tr = r - valueW - 6;
            float t = s.max > s.min ? (v - s.min) / (s.max - s.min) : 0;
            Draw.round(tl, cy - 1.5f, tr, cy + 1.5f, 1.5f, 0x26FFFFFF);
            Draw.round(tl, cy - 1.5f, tl + (tr - tl) * t, cy + 1.5f, 1.5f, ACCENT);
            float kx = tl + (tr - tl) * t;
            boolean dragging = dragSetting == s && dragModule == m;
            float ks = dragging || in(tl - 4, cy - 6, tr + 4, cy + 6) ? 4.5f : 3.5f;
            Draw.round(kx - ks, cy - ks, kx + ks, cy + ks, ks, 0xFFFFFFFF);
            Draw.text(text, r - Draw.width(text), cy - 4, TEXT);
            controlW = r - tl;
            hit(tl - 4, cy - 7, tr + 4, cy + 7, new Runnable() {
                public void run() {
                    dragModule = m;
                    dragSetting = s;
                    dragLeft = tl;
                    dragRight = tr;
                    drag(mouseX);
                }
            });
        } else if ("text".equals(s.type)) {
            boolean editing = key.equals(focus) && !focusHex;
            String text = editing ? edit.toString() : value != null ? value.getAsString() : "";
            float fw = 84;
            float fl = r - fw;
            Draw.round(fl, cy - 6.5f, r, cy + 6.5f, 4, FIELD);
            Draw.roundOutline(fl, cy - 6.5f, r, cy + 6.5f, 4, editing ? 0x995B8CFF : 0x26FFFFFF);
            String shown = Draw.tail(text, (int) fw - 10);
            Draw.text(shown, fl + 5, cy - 4, TEXT);
            caret(editing, fl + 5 + Draw.width(shown), cy - 5);
            controlW = fw;
            final String current = text;
            hit(fl, cy - 7, r, cy + 7, new Runnable() {
                public void run() {
                    focus = key;
                    focusHex = false;
                    edit.setLength(0);
                    edit.append(current);
                }
            });
        } else if ("choice".equals(s.type)) {
            String chosen = value != null ? value.getAsString() : "";
            float total = 2;
            for (String[] o : s.options) {
                total += Draw.width(o[1]) + 10;
            }
            float ox = r - total;
            Draw.round(ox, cy - 7, r, cy + 7, 4, FIELD);
            ox += 1;
            for (final String[] o : s.options) {
                float ow = Draw.width(o[1]) + 10;
                boolean sel = o[0].equals(chosen);
                boolean hover = in(ox, cy - 6, ox + ow, cy + 6);
                if (sel) {
                    Draw.round(ox, cy - 6, ox + ow, cy + 6, 3, 0x405B8CFF);
                }
                Draw.text(o[1], ox + 5, cy - 4, sel || hover ? TEXT : MUTED);
                hit(ox, cy - 6, ox + ow, cy + 6, new Runnable() {
                    public void run() {
                        set(m, s, new JsonPrimitive(o[0]));
                    }
                });
                ox += ow;
            }
            controlW = total;
        }
        Draw.text(Draw.trim(s.label, (int) (r - l - controlW - 8)), l, cy - 4, MUTED);
    }

    private void drawHudRow(final Module m, float l, float y, float r) {
        float h = 16;
        Draw.round(l, y, r, y + h, 4, 0x80080B12);
        Draw.icon(MOVE, l + 5, y + 4, 8, MUTED);
        float right = r - 4;
        if (mc.theWorld != null) {
            right = button(right, y + 2, "Edit HUD", null, new Runnable() {
                public void run() {
                    mc.displayGuiScreen(new GuiHudEditor());
                }
            });
        }
        if (NovoClient.config().has(m.id(), "x")) {
            right = button(right, y + 2, "Reset", RESET, new Runnable() {
                public void run() {
                    if (m instanceof HudModule) {
                        ((HudModule) m).resetPosition();
                    }
                    changed(true);
                }
            });
        }
        String hint = mc.theWorld != null ? "Move and resize in the HUD editor" : "Move and resize in-game with Right Shift";
        Draw.text(Draw.trim(hint, (int) (right - l - 20)), l + 16, y + 4, MUTED);
    }

    // ---- Changes ----

    private static String key(Module m, Catalog.Setting s) {
        return m.id() + "/" + s.key;
    }

    private void setEnabled(Module m, boolean on) {
        NovoClient.config().setEnabled(m.id(), on);
        changed(true);
    }

    private void set(Module m, Catalog.Setting s, JsonElement value) {
        NovoClient.config().setSetting(m.id(), s.key, value);
        changed(true);
    }

    private void changed(boolean save) {
        NovoClient.apply();
        if (save) {
            NovoClient.save();
            savedAt = System.currentTimeMillis();
        }
    }

    private void drag(int x) {
        Catalog.Setting s = dragSetting;
        float t = Math.max(0, Math.min(1, (x - dragLeft) / (dragRight - dragLeft)));
        float v = s.min + t * (s.max - s.min);
        if (s.step > 0) {
            v = s.min + Math.round((v - s.min) / s.step) * s.step;
        }
        v = Math.max(s.min, Math.min(s.max, v));
        JsonElement old = NovoClient.config().setting(dragModule.def, s.key);
        if (old == null || Math.abs(old.getAsFloat() - v) > 1e-4f) {
            NovoClient.config().setSetting(dragModule.id(), s.key, new JsonPrimitive(v));
            changed(false);
        }
    }

    private static String formatNumber(float v, float step) {
        int decimals = step >= 1 ? 0 : step >= 0.1f ? 1 : 2;
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    /** Ends editing a text or hex field, keeping what was typed. */
    private void commitFocus() {
        if (focus != null) {
            focus = null;
            NovoClient.save();
            savedAt = System.currentTimeMillis();
        }
    }

    private void typeIntoFocus(char c, int keyCode) {
        Module m = null;
        Catalog.Setting s = null;
        for (Module each : NovoClient.modules()) {
            for (Catalog.Setting setting : each.def.settings) {
                if (key(each, setting).equals(focus)) {
                    m = each;
                    s = setting;
                }
            }
        }
        if (m == null) {
            focus = null;
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (edit.length() > 0) {
                edit.setLength(edit.length() - 1);
            }
        } else if (focusHex) {
            if (Character.digit(c, 16) >= 0 && edit.length() < 6) {
                edit.append(Character.toUpperCase(c));
            }
        } else if (ChatAllowedCharacters.isAllowedCharacter(c) && edit.length() < s.maxLength) {
            edit.append(c);
        }
        if (focusHex) {
            if (edit.length() == 6) {
                NovoClient.config().setSetting(m.id(), s.key, new JsonPrimitive("#" + edit));
                changed(false);
            }
        } else {
            NovoClient.config().setSetting(m.id(), s.key, new JsonPrimitive(edit.toString()));
            changed(false);
        }
    }

    // ---- Input ----

    @Override
    protected void keyTyped(char c, int keyCode) throws IOException {
        if (focus != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_TAB) {
                commitFocus();
            } else {
                typeIntoFocus(c, keyCode);
            }
        } else if (keyCode == Keyboard.KEY_ESCAPE && palette != null) {
            palette = null;
        } else {
            super.keyTyped(c, keyCode);
        }
    }

    @Override
    protected void beforeClick() {
        commitFocus();
        paletteBeforeClick = palette;
        palette = null;
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (dragSetting != null) {
            drag(x);
        }
    }

    @Override
    protected void mouseReleased(int x, int y, int state) {
        if (dragSetting != null) {
            dragSetting = null;
            dragModule = null;
            changed(true);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        NovoClient.save();
    }
}
