package com.novomc.client.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import com.novomc.client.modules.NickHiderModule;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Smooth TrueType text (Poppins, SIL OFL) for NovoMC's menus and HUD.
 *
 * Glyphs are rendered once by AWT into a texture atlas at {@link #OVERSAMPLE}x
 * the GUI size and drawn as textured quads, so text stays crisp at every GUI
 * scale. Layout matches vanilla's FontRenderer: `y` is the top of a 9 px line
 * and Minecraft's § color/format codes work (§l switches to the bold weight).
 */
public final class NovoFont {
    /** Atlas pixels per GUI pixel. */
    private static final int OVERSAMPLE = 4;
    /** Glyph height in GUI pixels; Poppins at this size lines up with vanilla's 8 px caps. */
    private static final float SIZE = 8.5f;
    private static final int ATLAS = 1024;
    private static final int FIRST = 32;
    private static final int LAST = 382;
    /** Vanilla's 16 chat colors (§0-§f). */
    private static final int[] COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    public static final NovoFont REGULAR = new NovoFont("Poppins-Regular");
    public static final NovoFont MEDIUM = new NovoFont("Poppins-Medium");
    public static final NovoFont SEMIBOLD = new NovoFont("Poppins-SemiBold");
    public static final NovoFont BOLD = new NovoFont("Poppins-Bold");

    private final String file;
    private boolean loaded;
    private boolean failed;
    private int texture;
    /** Per char: u, v, width, height (atlas px), advance (GUI px). */
    private final float[][] glyphs = new float[LAST + 1][];
    private float ascent;

    private NovoFont(String file) {
        this.file = file;
    }

    /** False if the font couldn't be loaded; callers then fall back to vanilla text. */
    public boolean available() {
        load();
        return !failed;
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            InputStream in = NovoFont.class.getResourceAsStream("/assets/novomc/fonts/" + file + ".ttf");
            Font font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(SIZE * OVERSAMPLE);
            in.close();
            BufferedImage image = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setFont(font);
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            int lineH = fm.getAscent() + fm.getDescent() + 2;
            ascent = fm.getAscent() / (float) OVERSAMPLE;
            int x = 1;
            int y = 1;
            for (int c = FIRST; c <= LAST; c++) {
                if (!font.canDisplay((char) c)) {
                    continue;
                }
                String s = String.valueOf((char) c);
                float advance = (float) font.getStringBounds(s, g.getFontRenderContext()).getWidth();
                int w = (int) Math.ceil(advance) + 4;
                if (x + w >= ATLAS) {
                    x = 1;
                    y += lineH;
                }
                if (y + lineH >= ATLAS) {
                    break;
                }
                g.drawString(s, x + 2, y + fm.getAscent());
                glyphs[c] = new float[] {x, y, w, lineH, advance / OVERSAMPLE};
                x += w;
            }
            g.dispose();
            DynamicTexture tex = new DynamicTexture(image);
            texture = tex.getGlTextureId();
            GlStateManager.bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        } catch (Exception e) {
            failed = true;
            System.err.println("[NovoMC] Couldn't load font " + file + ": " + e);
        }
    }

    /** The glyph for `c`, or null if Poppins doesn't have it. */
    private float[] glyph(char c) {
        return c <= LAST ? glyphs[c] : null;
    }

    /**
     * Characters that take no space. Servers (Hypixel's sidebar especially) hide
     * these inside text; drawing them as "?" garbles the line.
     */
    private static boolean invisible(char c) {
        int type = Character.getType(c);
        return type == Character.FORMAT || type == Character.CONTROL || type == Character.SURROGATE
                || type == Character.UNASSIGNED || (c >= 0xFE00 && c <= 0xFE0F);
    }

    /** Minecraft's own font, for symbols Poppins lacks (it covers all of Unicode's BMP). */
    private static FontRenderer vanilla() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    /** Width in GUI pixels, skipping § codes (bold counts with the bold font). */
    public float width(String text) {
        load();
        if (failed || text == null) {
            return 0;
        }
        // Nick Hider applies here too (it hooks the vanilla font, which this one bypasses).
        text = NickHiderModule.replace(text);
        float w = 0;
        NovoFont font = this;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                font = code == 'l' ? BOLD : "0123456789abcdefr".indexOf(code) >= 0 ? this : font;
                font.load();
                continue;
            }
            float[] g = font.glyph(c);
            if (g != null) {
                w += g[4];
            } else if (!invisible(c)) {
                w += vanilla().getCharWidth(c);
            }
        }
        return w;
    }

    /** Draws `text` with its top at `y` (a vanilla 9 px line), with an optional drop shadow. */
    public void draw(String text, float x, float y, int argb, boolean shadow) {
        load();
        if (failed || text == null || text.isEmpty()) {
            return;
        }
        text = NickHiderModule.replace(text);
        if ((argb & 0xFC000000) == 0) {
            argb |= 0xFF000000;
        }
        if (shadow) {
            int s = (argb & 0xFCFCFC) >> 2 | (argb & 0xFF000000);
            render(text, x + 0.5f, y + 0.5f, s, true);
        }
        render(text, x, y, argb, false);
    }

    private void render(String text, float x, float y, int argb, boolean shadow) {
        float alpha = (argb >>> 24) / 255f;
        int color = argb & 0xFFFFFF;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        // Baseline where vanilla's 8 px caps would sit.
        float baseline = y + 7.2f;
        float cx = x;
        NovoFont font = this;
        Tessellator t = Tessellator.getInstance();
        WorldRenderer w = t.getWorldRenderer();
        int bound = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                int idx = "0123456789abcdef".indexOf(code);
                if (idx >= 0) {
                    color = shadow ? (COLORS[idx] & 0xFCFCFC) >> 2 : COLORS[idx];
                    font = this;
                } else if (code == 'r') {
                    color = argb & 0xFFFFFF;
                    font = this;
                } else if (code == 'l') {
                    font = BOLD;
                    font.load();
                }
                continue;
            }
            float[] g = font.glyph(c);
            if (g == null) {
                if (!invisible(c)) {
                    // A symbol Poppins lacks: draw it with Minecraft's font, then resume our batch.
                    if (bound != -1) {
                        t.draw();
                        bound = -1;
                    }
                    vanilla().drawString(String.valueOf(c), cx, y, Math.round(alpha * 255) << 24 | color, false);
                    cx += vanilla().getCharWidth(c);
                    GlStateManager.enableBlend();
                    GlStateManager.color(1, 1, 1, 1);
                }
                continue;
            }
            if (font.texture != bound) {
                if (bound != -1) {
                    t.draw();
                }
                GlStateManager.bindTexture(font.texture);
                w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                bound = font.texture;
            }
            if (c != ' ') {
                float qx = cx - 2f / OVERSAMPLE;
                float qy = baseline - font.ascent;
                float qw = g[2] / OVERSAMPLE;
                float qh = g[3] / OVERSAMPLE;
                float u0 = g[0] / ATLAS, v0 = g[1] / ATLAS, u1 = (g[0] + g[2]) / ATLAS, v1 = (g[1] + g[3]) / ATLAS;
                int r = (color >> 16) & 255, gr = (color >> 8) & 255, b = color & 255, a = Math.round(alpha * 255);
                w.pos(qx, qy + qh, 0).tex(u0, v1).color(r, gr, b, a).endVertex();
                w.pos(qx + qw, qy + qh, 0).tex(u1, v1).color(r, gr, b, a).endVertex();
                w.pos(qx + qw, qy, 0).tex(u1, v0).color(r, gr, b, a).endVertex();
                w.pos(qx, qy, 0).tex(u0, v0).color(r, gr, b, a).endVertex();
            }
            cx += g[4];
        }
        if (bound != -1) {
            t.draw();
        }
    }

    /** Lines of `text` wrapped at word boundaries to `maxWidth` GUI pixels. */
    public java.util.List<String> wrap(String text, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && width(next) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** `text` cut to fit `maxWidth` GUI pixels. */
    public String trim(String text, float maxWidth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (width(sb.toString()) > maxWidth) {
                sb.setLength(sb.length() - 1);
                break;
            }
        }
        return sb.toString();
    }
}
