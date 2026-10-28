package com.novomc.modern.ui;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Novo Client's look on modern Minecraft, matching the 1.8.9 menus: the
 * Poppins font, dark rounded widgets with a blue accent, eased hovers and a
 * short fade-in when a menu opens. Widget mixins call into this.
 */
public final class NovoUi {
    public static final int ACCENT = 0xFF5B8CFF;
    public static final int PURPLE = 0xFFB45BFF;
    public static final int CYAN = 0xFF22D3EE;
    public static final int TEXT = 0xFFE8ECF4;
    public static final int MUTED = 0xFF8A93A6;
    public static final int PANEL = 0xEE0C1019;

    private static final Style FONT = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("novomc", "poppins")));
    private static final Style BOLD = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("novomc", "poppins_bold")));
    private static final long FADE_MS = 180;
    private static final Map<Object, float[]> HOVER = new WeakHashMap<Object, float[]>();

    /** Whether the Novo look is on (the Custom Menus mod). */
    public static volatile boolean enabled = true;
    /** Whether the Novo title screen replaces vanilla's (the Main Menu mod). */
    public static volatile boolean mainMenu = true;
    /** Whether the Novo loading screen replaces Mojang's (the Loading Screen mod). */
    public static volatile boolean loading = true;
    /** Widgets that show only their value (rows in Novo's settings screens). */
    public static final Set<Object> VALUE_ONLY = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    /** Lets the next vanilla Options or Video Settings screen through (More options). */
    public static boolean vanillaNext;
    private static long openedAt;

    private NovoUi() {}

    public static Identifier tex(String path) {
        return Identifier.fromNamespaceAndPath("novomc", "textures/gui/" + path + ".png");
    }

    public static Component text(String s) {
        return Component.literal(s).withStyle(FONT);
    }

    public static Component bold(String s) {
        return Component.literal(s).withStyle(BOLD);
    }

    public static int width(Component c) {
        return Minecraft.getInstance().font.width(c);
    }

    /** Trim with an ellipsis without splitting UTF-16 surrogate pairs. */
    public static Component trim(String value, int maxWidth) {
        String valueText = value == null ? "" : value;
        if (maxWidth <= 0) {
            return text("");
        }
        if (width(text(valueText)) <= maxWidth) {
            return text(valueText);
        }
        String ellipsis = "…";
        int end = valueText.length();
        while (end > 0) {
            end = valueText.offsetByCodePoints(end, -1);
            String candidate = valueText.substring(0, end) + ellipsis;
            if (width(text(candidate)) <= maxWidth) {
                return text(candidate);
            }
        }
        return text(ellipsis);
    }

    public static void draw(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.text(Minecraft.getInstance().font, c, x, y, color, false);
    }

    public static void centered(GuiGraphicsExtractor g, Component c, int cx, int y, int color) {
        draw(g, c, cx - width(c) / 2, y, color);
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    public static int blend(int a, int b, float t) {
        int r = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 0xFF;
            int cb = b >>> shift & 0xFF;
            r |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return r;
    }

    /** A rectangle with its four corners cut off: a small rounded panel. */
    public static void round(GuiGraphicsExtractor g, int l, int t, int r, int b, int color) {
        g.fill(l + 1, t, r - 1, b, color);
        g.fill(l, t + 1, l + 1, b - 1, color);
        g.fill(r - 1, t + 1, r, b - 1, color);
    }

    public static void roundOutline(GuiGraphicsExtractor g, int l, int t, int r, int b, int color) {
        g.fill(l + 1, t, r - 1, t + 1, color);
        g.fill(l + 1, b - 1, r - 1, b, color);
        g.fill(l, t + 1, l + 1, b - 1, color);
        g.fill(r - 1, t + 1, r, b - 1, color);
    }

    /** Moves a widget's hover amount toward on/off, frame-rate independent. */
    public static float hover(Object widget, boolean on) {
        float[] state = HOVER.computeIfAbsent(widget, k -> new float[] {on ? 1 : 0, Util.getMillis()});
        long now = Util.getMillis();
        float k = 1 - (float) Math.exp(-Math.min(0.1, (now - state[1]) / 1000.0) * 16);
        state[0] += ((on ? 1 : 0) - state[0]) * k;
        state[1] = now;
        return state[0];
    }

    /** A button: dark glass, blue edge and an underline growing from the centre on hover. */
    public static void button(GuiGraphicsExtractor g, Object widget, int x, int y, int w, int h, boolean hovered, boolean active) {
        float t = active ? hover(widget, hovered) : 0;
        round(g, x, y, x + w, y + h, active ? blend(0x99101521, 0xD01B2540, t) : 0x66101521);
        if (t > 0.02f) {
            int half = Math.round((w / 2f - 1) * t);
            g.fill(x + w / 2 - half, y + h - 1, x + w / 2 + half, y + h, withAlpha(ACCENT, Math.round(t * 220)));
        }
        roundOutline(g, x, y, x + w, y + h, blend(0x24FFFFFF, withAlpha(ACCENT, 0xCC), t));
    }

    public static void label(GuiGraphicsExtractor g, Component message, int x, int y, int w, int h, boolean hovered, boolean active) {
        Component c = text(message.getString());
        int color = !active ? MUTED : hovered ? 0xFFFFFFFF : TEXT;
        centered(g, c, x + w / 2, y + (h - 8) / 2, color);
    }

    /** A slider: track along the bottom with the value filled in blue, and the label above. */
    public static void slider(GuiGraphicsExtractor g, Object widget, int x, int y, int w, int h, double value, Component message, boolean hovered,
                              boolean active) {
        float t = hover(widget, hovered);
        round(g, x, y, x + w, y + h, blend(0x99101521, 0xC0162036, t));
        roundOutline(g, x, y, x + w, y + h, blend(0x24FFFFFF, withAlpha(ACCENT, 0x80), t));
        int track = y + h - 4;
        int knob = x + 6 + (int) Math.round((w - 12) * Math.max(0, Math.min(1, value)));
        g.fill(x + 6, track - 1, x + w - 6, track + 1, 0xFF2A3142);
        g.fill(x + 6, track - 1, knob, track + 1, ACCENT);
        round(g, knob - 3, track - 3, knob + 3, track + 3, 0xFFFFFFFF);
        Component c = text(message.getString());
        centered(g, c, x + w / 2, y + (h - 12) / 2, active ? TEXT : MUTED);
    }

    public static void field(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        round(g, x, y, x + w, y + h, 0xFF0A0E16);
        roundOutline(g, x, y, x + w, y + h, focused ? withAlpha(ACCENT, 0xCC) : 0x30FFFFFF);
    }

    /** Draws a 16x16 Novo icon (white glyph) tinted with {@code color}. */
    public static void icon(GuiGraphicsExtractor g, String name, int x, int y, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex("icons/" + name), x, y, 0, 0, size, size, size, size, color);
    }

    // ---- Menu fade-in ----

    /** Menus fade in when they open; screens used mid-game (inventories, chat) open instantly. */
    public static void opened(Screen screen) {
        if (enabled && !(screen instanceof AbstractContainerScreen) && !(screen instanceof ChatScreen)) {
            openedAt = Util.getMillis();
        }
    }

    public static void fadeIn(GuiGraphicsExtractor g) {
        long age = Util.getMillis() - openedAt;
        if (!enabled || age >= FADE_MS) {
            return;
        }
        float t = age / (float) FADE_MS;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), withAlpha(0xFF070A12, Math.round((1 - t) * (1 - t) * 255)));
    }
}
