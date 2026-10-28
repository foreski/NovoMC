package com.novomc.modern.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The look shared by Novo's full-screen panels on modern Minecraft (Mods,
 * Options, Video Settings, Trainers), as on 1.8.9: a glass panel, eyebrow and
 * gradient title, chips, and a scrolling content area. Everything is drawn by
 * hand; click targets are recorded while drawing and resolved on click.
 */
public abstract class NovoPanel extends Screen {
    protected static final int PAD = 12;

    private record Hit(int l, int t, int r, int b, Runnable action) {}

    protected final @Nullable Screen parent;
    private final List<Hit> hits = new ArrayList<Hit>();
    private final Map<String, Float> anim = new HashMap<String, Float>();
    protected float scroll;
    protected float scrollTarget;
    private long lastFrame;
    private float k = 1;
    protected int mouseX;
    protected int mouseY;
    private int clipT = Integer.MIN_VALUE;
    private int clipB = Integer.MAX_VALUE;

    protected NovoPanel(@Nullable Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
    }

    /** Panel bounds {left, top, right, bottom} of the content (panel drawn already). */
    protected int[] beginFrame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 1 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        k = 1 - (float) Math.exp(-dt * 16);
        hits.clear();
        int pw = Math.min(width - 16, 540);
        int px = (width - pw) / 2;
        NovoUi.round(g, px, 8, px + pw, height - 8, NovoUi.PANEL);
        NovoUi.roundOutline(g, px, 8, px + pw, height - 8, 0x22FFFFFF);
        return new int[] {px + PAD, 8, px + pw - PAD, height - 8};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x90060810);
    }

    /** Eyebrow, gradient title, subtitle and close button. */
    protected void header(GuiGraphicsExtractor g, int l, int r, int top, String title, String subtitle) {
        NovoUi.icon(g, "swords", l, top + 9, 8, NovoUi.CYAN);
        NovoUi.draw(g, NovoUi.text("N O V O   C L I E N T"), l + 11, top + 9, NovoUi.CYAN);
        g.pose().pushMatrix();
        g.pose().translate(l, top + 21);
        g.pose().scale(2, 2);
        int x = 0;
        for (int i = 0; i < title.length(); i++) {
            Component c = NovoUi.bold(String.valueOf(title.charAt(i)));
            NovoUi.draw(g, c, x, 0, NovoUi.blend(0xFFA9C2FF, 0xFFD6A4FF, i / (float) Math.max(1, title.length() - 1)));
            x += NovoUi.width(c);
        }
        g.pose().popMatrix();
        NovoUi.draw(g, NovoUi.text(subtitle), l, top + 44, NovoUi.MUTED);
        int cx = r - 16;
        int cy = top + 9;
        boolean hover = in(cx, cy, cx + 16, cy + 16);
        NovoUi.round(g, cx, cy, cx + 16, cy + 16, hover ? 0x33FFFFFF : 0x14FFFFFF);
        NovoUi.icon(g, "x", cx + 4, cy + 4, 8, hover ? NovoUi.TEXT : NovoUi.MUTED);
        hit(cx, cy, cx + 16, cy + 16, this::onClose);
    }

    /** A row of chips; returns the bottom edge. */
    protected int chips(GuiGraphicsExtractor g, String[][] chips, String active, int l, int r, int top, java.util.function.Consumer<String> pick) {
        int x = l;
        int y = top;
        for (String[] c : chips) {
            Component label = NovoUi.text(c[1]);
            int w = NovoUi.width(label) + 16;
            if (x + w > r) {
                x = l;
                y += 20;
            }
            boolean on = c[0].equals(active);
            boolean hover = in(x, y, x + w, y + 16);
            NovoUi.round(g, x, y, x + w, y + 16, on ? 0x2E5B8CFF : hover ? 0x14FFFFFF : 0x00000000);
            NovoUi.roundOutline(g, x, y, x + w, y + 16, on ? 0x995B8CFF : 0x26FFFFFF);
            NovoUi.draw(g, label, x + 8, y + 4, on || hover ? NovoUi.TEXT : NovoUi.MUTED);
            String id = c[0];
            hit(x, y, x + w, y + 16, () -> pick.accept(id));
            x += w + 4;
        }
        return y + 16;
    }

    /** A switch, as on 1.8.9. */
    protected void toggle(GuiGraphicsExtractor g, int x, int y, String key, boolean on) {
        float t = ease(key, on ? 1 : 0);
        NovoUi.round(g, x, y, x + 24, y + 12, NovoUi.blend(0xFF2A3142, NovoUi.ACCENT, t));
        int kx = x + 1 + Math.round(12 * t);
        NovoUi.round(g, kx, y + 1, kx + 10, y + 11, 0xFFFFFFFF);
    }

    /** Eases scrolling toward its target, clamped to the content; returns the max scroll. */
    protected float updateScroll(float content, float viewport) {
        float max = Math.max(0, content - viewport);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scroll += (scrollTarget - scroll) * k;
        if (Math.abs(scrollTarget - scroll) < 0.05f) {
            scroll = scrollTarget;
        }
        return max;
    }

    protected void scrollbar(GuiGraphicsExtractor g, int r, int top, int bottom, float max) {
        if (max > 0) {
            float track = bottom - top;
            float thumb = Math.max(16, track * track / (track + max));
            int ty = Math.round(top + (track - thumb) * (scroll / max));
            g.fill(r + 4, ty, r + 6, ty + Math.round(thumb), 0x40FFFFFF);
        }
    }

    protected void clip(GuiGraphicsExtractor g, int l, int t, int r, int b) {
        clipT = t;
        clipB = b;
        g.enableScissor(l, t, r, b);
    }

    protected void noClip(GuiGraphicsExtractor g) {
        clipT = Integer.MIN_VALUE;
        clipB = Integer.MAX_VALUE;
        g.disableScissor();
    }

    protected boolean in(int l, int t, int r, int b) {
        return mouseX >= l && mouseX < r && mouseY >= t && mouseY < b && mouseY >= clipT && mouseY < clipB;
    }

    /** Records a click target, limited to the visible (clipped) part. */
    protected void hit(int l, int t, int r, int b, Runnable action) {
        int ct = Math.max(t, clipT);
        int cb = Math.min(b, clipB);
        if (cb > ct) {
            hits.add(new Hit(l, ct, r, cb, action));
        }
    }

    protected float ease(String id, float target) {
        Float v = anim.get(id);
        float next = v == null ? target : v + (target - v) * k;
        if (Math.abs(target - next) < 0.002f) {
            next = target;
        }
        anim.put(id, next);
        return next;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (event.x() >= h.l() && event.x() < h.r() && event.y() >= h.t() && event.y() < h.b()) {
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        scrollTarget -= (float) Math.signum(v) * 36;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
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
