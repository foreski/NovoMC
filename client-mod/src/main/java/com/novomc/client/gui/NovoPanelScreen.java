package com.novomc.client.gui;

import com.novomc.client.util.Draw;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The look shared by NovoMC's full-screen panels (Mods, Resource Packs), in the
 * style of the launcher: a glass panel, eyebrow + gradient title, a Mods /
 * Resource Packs tab switch, chips, a search box and a scrolling content area.
 *
 * Everything is drawn by hand; click targets are recorded while drawing
 * (clipped to what is visible) and resolved in {@link #mouseClicked}.
 */
public abstract class NovoPanelScreen extends GuiScreen {
    protected static final int ACCENT = NovoButton.ACCENT;
    protected static final int PURPLE = 0xFFB45BFF;
    protected static final int CYAN = 0xFF22D3EE;
    protected static final int GREEN = 0xFF4ADE80;
    protected static final int RED = 0xFFF87171;
    protected static final int TEXT = 0xFFE8ECF4;
    protected static final int MUTED = 0xFF8A93A6;
    protected static final int FIELD = 0xFF0A0E16;
    protected static final int PAD = 12;

    protected static ResourceLocation icon(String name) {
        return new ResourceLocation("novomc", "textures/gui/icons/" + name + ".png");
    }

    protected static final ResourceLocation SWORDS = icon("swords");
    protected static final ResourceLocation CLOSE = icon("x");
    protected static final ResourceLocation SEARCH = icon("search");
    protected static final ResourceLocation SPARKLES = icon("sparkles");

    /** A clickable area recorded while drawing, clipped to what was visible. */
    private static final class Hit {
        final float l, t, r, b;
        final Runnable action;

        Hit(float l, float t, float r, float b, Runnable action) {
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
            this.action = action;
        }
    }

    protected final GuiScreen parent;
    private final List<Hit> hits = new ArrayList<Hit>();
    /** Animated 0..1 values (hovers, switches, expansion). */
    protected final Map<String, Float> anim = new HashMap<String, Float>();
    protected final StringBuilder search = new StringBuilder();

    protected float scroll;
    protected float scrollTarget;
    private long lastFrame;
    /** Per-frame smoothing factor for {@link #ease}. */
    protected float k;
    protected int mouseX, mouseY;
    /** Current clip rectangle in GUI coordinates. */
    protected float clipL, clipT, clipR, clipB;

    protected NovoPanelScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
    }

    /** Which header tab this screen is: "mods" or "packs". */
    protected abstract String tab();

    /** The header's tab switch, as {id, label} pairs. */
    protected String[][] headerTabs() {
        return new String[][] {{"mods", "Mods"}, {"packs", "Resource Packs"}};
    }

    /** A header tab was clicked; the default swaps between the two panel screens. */
    protected void headerTabSelected(String id) {
        mc.displayGuiScreen("mods".equals(id) ? new GuiModules(parent) : new GuiResourcePacks(parent));
    }

    /** Typing goes into the search box unless a subclass has another field focused. */
    protected boolean searchFocused() {
        return true;
    }

    /** Called before a click is resolved (e.g. to end editing a field). */
    protected void beforeClick() {}

    /** Called when the search text changes. */
    protected void searchChanged() {
        scrollTarget = 0;
    }

    // ---- Frame and panel ----

    /** Starts a frame; returns the panel bounds {left, top, right, bottom} (already drawn). */
    protected int[] beginFrame(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 1 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        k = 1 - (float) Math.exp(-dt * 16);
        hits.clear();
        noClip();
        if (mc.theWorld != null) {
            Draw.rect(0, 0, width, height, 0xC8060810);
        } else {
            drawDefaultBackground();
        }
        int preferredWidth = Math.min(760, Math.max(420, Math.round(width * 0.68f)));
        int pw = Math.max(0, Math.min(width - 24, preferredWidth));
        int px = (width - pw) / 2;
        int py = 8;
        int pb = height - 8;
        Draw.round(px, py, px + pw, pb, 8, 0xEE0C1019);
        Draw.roundOutline(px, py, px + pw, pb, 8, 0x22FFFFFF);
        return new int[] {px + PAD, py, px + pw - PAD, pb};
    }

    /**
     * Eyebrow, gradient title, subtitle and the tab switch + close button.
     * Returns the left edge of the tabs, for extras drawn beside them.
     */
    protected float drawHeader(int l, int r, int top, String title, String subtitle) {
        Draw.icon(SWORDS, l, top + 10, 8, CYAN);
        Draw.spaced("NOVO CLIENT", l + 11, top + 10, CYAN, 1.2f);

        GlStateManager.pushMatrix();
        GlStateManager.translate(l, top + 22, 0);
        GlStateManager.scale(2, 2, 1);
        float x = 0;
        for (int i = 0; i < title.length(); i++) {
            String c = String.valueOf(title.charAt(i));
            Draw.text(c, x, 0, Draw.blend(0xFFA9C2FF, 0xFFD6A4FF, i / (float) Math.max(1, title.length() - 1)));
            x += Draw.width(c);
        }
        GlStateManager.popMatrix();
        Draw.text(Draw.trim(subtitle, r - l - searchWidth(l, r) - headerReserve - 8), l, top + 44, MUTED);

        int cs = 16;
        int cx = r - cs;
        int cy = top + 9;
        boolean closeHover = in(cx, cy, cx + cs, cy + cs);
        Draw.round(cx, cy, cx + cs, cy + cs, 4, closeHover ? 0x33FFFFFF : 0x14FFFFFF);
        Draw.icon(CLOSE, cx + 4, cy + 4, 8, closeHover ? TEXT : MUTED);
        hit(cx, cy, cx + cs, cy + cs, new Runnable() {
            public void run() {
                back();
            }
        });

        String[][] tabs = headerTabs();
        float total = 2;
        for (String[] t : tabs) {
            total += Draw.width(t[1]) + 14;
        }
        float tl = cx - 6 - total;
        Draw.round(tl, cy, cx - 6, cy + cs, 8, 0x99080B12);
        Draw.roundOutline(tl, cy, cx - 6, cy + cs, 8, 0x22FFFFFF);
        float tx = tl + 1;
        for (final String[] t : tabs) {
            float w = Draw.width(t[1]) + 14;
            boolean active = t[0].equals(tab());
            boolean hover = in(tx, cy, tx + w, cy + cs);
            if (active) {
                Draw.round(tx, cy + 1, tx + w, cy + cs - 1, 7, 0x405B8CFF);
            }
            Draw.text(t[1], tx + 7, cy + 4, active || hover ? TEXT : MUTED);
            if (!active) {
                hit(tx, cy, tx + w, cy + cs, new Runnable() {
                    public void run() {
                        headerTabSelected(t[0]);
                    }
                });
            }
            tx += w;
        }
        return tl;
    }

    /** Width of the header search box for a panel spanning l..r. */
    /** Extra room subclasses keep left of the search box (for their own header buttons). */
    protected int headerReserve;

    protected int searchWidth(int l, int r) {
        return Math.min(130, (r - l) / 3);
    }

    /** The search box; typing goes here while {@link #searchFocused()}. */
    protected void drawSearch(float sx, float sy, float r, String placeholder) {
        int searchW = Math.round(r - sx);
        int h = 16;
        boolean focused = searchFocused();
        Draw.round(sx, sy, sx + searchW, sy + h, 8, 0x99080B12);
        Draw.roundOutline(sx, sy, sx + searchW, sy + h, 8, focused && search.length() > 0 ? 0x995B8CFF : 0x26FFFFFF);
        Draw.icon(SEARCH, sx + 7, sy + 4, 8, MUTED);
        float textX = sx + 19;
        int textW = searchW - 19 - (search.length() > 0 ? 14 : 6);
        if (search.length() == 0) {
            Draw.text(Draw.trim(placeholder, textW), textX, sy + 4, 0xFF5E6676);
        } else {
            String shown = Draw.tail(search.toString(), textW);
            Draw.text(shown, textX, sy + 4, TEXT);
            caret(focused, textX + Draw.width(shown), sy + 3);
            final float clearX = sx + searchW - 13;
            Draw.icon(CLOSE, clearX + 2, sy + 5, 6, in(clearX, sy, clearX + 12, sy + h) ? TEXT : MUTED);
            hit(clearX, sy, clearX + 12, sy + h, new Runnable() {
                public void run() {
                    search.setLength(0);
                    searchChanged();
                }
            });
        }
    }

    /**
     * A row of chips ({id, label}); the active one is highlighted and clicking
     * one calls {@link #chipSelected}. Wraps onto more rows; returns the bottom edge.
     */
    protected int drawChips(final String group, List<String[]> chips, String active, int l, int r, int top) {
        int h = 16;
        float x = l;
        float y = top;
        for (final String[] c : chips) {
            int w = Draw.width(c[1]) + 16;
            if (x + w > r) {
                x = l;
                y += h + 4;
            }
            boolean on = c[0].equals(active);
            boolean hover = in(x, y, x + w, y + h);
            Draw.round(x, y, x + w, y + h, 8, on ? 0x2E5B8CFF : hover ? 0x14FFFFFF : 0x00000000);
            Draw.roundOutline(x, y, x + w, y + h, 8, on ? 0x995B8CFF : 0x26FFFFFF);
            Draw.text(c[1], x + 8, y + 4, on || hover ? TEXT : MUTED);
            hit(x, y, x + w, y + h, new Runnable() {
                public void run() {
                    chipSelected(group, c[0]);
                }
            });
            x += w + 4;
        }
        return Math.round(y + h);
    }

    protected void chipSelected(String group, String id) {}

    /** Smooths scrolling toward the target, clamped to the content; returns the max scroll. */
    protected float updateScroll(float content, float viewport) {
        float max = Math.max(0, content - viewport);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scroll += (scrollTarget - scroll) * k;
        if (Math.abs(scrollTarget - scroll) < 0.05f) {
            scroll = scrollTarget;
        }
        return max;
    }

    protected void drawScrollbar(int r, int top, int bottom, float max) {
        if (max > 0) {
            float track = bottom - top;
            float thumb = Math.max(16, track * track / (track + max));
            float ty = top + (track - thumb) * (scroll / max);
            Draw.round(r + 4, ty, r + 6, ty + thumb, 1, 0x40FFFFFF);
        }
    }

    // ---- Small widgets ----

    /**
     * A flat list row, as on the modern client: no outline, lighter on hover
     * (t = 0..1) and, when {@code accent} isn't 0, a thin bar on the left (for "on").
     */
    protected void flatRow(float l, float t, float r, float b, float hover, int accent) {
        Draw.round(l, t, r, b, 5, Draw.blend(0x0AFFFFFF, 0x16FFFFFF, hover));
        if (accent != 0) {
            Draw.rect(Math.round(l), Math.round(t + 6), Math.round(l) + 2, Math.round(b - 6), accent);
        }
    }

    protected void toggle(float x, float y, float w, float h, float t) {
        Draw.round(x, y, x + w, y + h, h / 2, Draw.blend(0xFF2A3142, ACCENT, t));
        float d = h - 2;
        float kx = x + 1 + (w - h) * t;
        Draw.round(kx, y + 1, kx + d, y + 1 + d, d / 2, 0xFFFFFFFF);
    }

    protected void caret(boolean show, float x, float y) {
        if (show && System.currentTimeMillis() / 500 % 2 == 0) {
            Draw.rect(Math.round(x + 1), Math.round(y), Math.round(x + 1) + 1, Math.round(y) + 10, 0xFFFFFFFF);
        }
    }

    /** A small text button ending at `right`; returns its left edge (minus a gap). */
    protected float button(float right, float y, String text, ResourceLocation icon, Runnable action) {
        float w = Draw.width(text) + 10 + (icon != null ? 10 : 0);
        float l = right - w;
        boolean hover = in(l, y, right, y + 12);
        Draw.round(l, y, right, y + 12, 3, hover ? 0x335B8CFF : 0x1AFFFFFF);
        float tx = l + 5;
        if (icon != null) {
            Draw.icon(icon, tx, y + 2, 8, hover ? TEXT : MUTED);
            tx += 10;
        }
        Draw.text(text, tx, y + 2, hover ? TEXT : 0xFFB9CBFF);
        hit(l, y, right, y + 12, action);
        return l - 4;
    }

    // ---- Input ----

    @Override
    protected void keyTyped(char c, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (search.length() > 0) {
                search.setLength(0);
                searchChanged();
            } else {
                back();
            }
        } else if (keyCode == Keyboard.KEY_BACK) {
            if (search.length() > 0) {
                search.setLength(search.length() - 1);
                searchChanged();
            }
        } else if (ChatAllowedCharacters.isAllowedCharacter(c) && search.length() < 32) {
            search.append(c);
            searchChanged();
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        if (button != 0) {
            return;
        }
        beforeClick();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.l && x < h.r && y >= h.t && y < h.b) {
                h.action.run();
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollTarget -= Integer.signum(wheel) * 36;
        }
    }

    protected void back() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---- Helpers ----

    protected boolean in(float l, float t, float r, float b) {
        return mouseX >= l && mouseX < r && mouseY >= t && mouseY < b
                && mouseX >= clipL && mouseX < clipR && mouseY >= clipT && mouseY < clipB;
    }

    /** Records a click target, limited to the visible (clipped) part. */
    protected void hit(float l, float t, float r, float b, Runnable action) {
        float cl = Math.max(l, clipL);
        float ct = Math.max(t, clipT);
        float cr = Math.min(r, clipR);
        float cb = Math.min(b, clipB);
        if (cr > cl && cb > ct) {
            hits.add(new Hit(cl, ct, cr, cb, action));
        }
    }

    /** Moves an animated value toward `target` and returns it. */
    protected float ease(String id, float target) {
        Float v = anim.get(id);
        float next = v == null ? target : v + (target - v) * k;
        if (Math.abs(target - next) < 0.002f) {
            next = target;
        }
        anim.put(id, next);
        return next;
    }

    protected void clip(float l, float t, float r, float b) {
        clipL = l;
        clipT = t;
        clipR = r;
        clipB = b;
        int f = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int sl = (int) Math.floor(l * f);
        int sb = (int) Math.ceil(b * f);
        GL11.glScissor(sl, mc.displayHeight - sb, Math.max(0, (int) Math.ceil(r * f) - sl), Math.max(0, sb - (int) Math.floor(t * f)));
    }

    protected void noClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        clipL = Integer.MIN_VALUE;
        clipT = Integer.MIN_VALUE;
        clipR = Integer.MAX_VALUE;
        clipB = Integer.MAX_VALUE;
    }
}
