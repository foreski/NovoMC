package com.novomc.client.util;

import com.novomc.client.modules.ModernFontModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Small drawing helpers for HUD code. */
public final class Draw {
    /** For instance-only Gui methods such as drawTexturedModalRect. */
    public static final Gui GUI = new Gui();

    private Draw() {}

    public static void rect(int left, int top, int right, int bottom, int argb) {
        Gui.drawRect(left, top, right, bottom, argb);
    }

    /** A 1px outline inside the given bounds. */
    public static void outline(int left, int top, int right, int bottom, int argb) {
        rect(left, top, right, top + 1, argb);
        rect(left, bottom - 1, right, bottom, argb);
        rect(left, top + 1, left + 1, bottom - 1, argb);
        rect(right - 1, top + 1, right, bottom - 1, argb);
    }

    /** True while NovoMC HUD modules draw, so Modern Font can treat the HUD separately. */
    public static boolean inHud;

    /** Poppins when Modern Font is on for this context, else Minecraft's pixel font. */
    private static NovoFont font() {
        return ModernFontModule.use(inHud) && NovoFont.REGULAR.available() ? NovoFont.REGULAR : null;
    }

    public static void text(String s, float x, float y, int argb) {
        NovoFont f = font();
        if (f != null) {
            f.draw(s, x, y, argb, true);
        } else {
            Minecraft.getMinecraft().fontRendererObj.drawString(s, x, y, argb, true);
        }
    }

    public static void text(String s, float x, float y, int argb, boolean shadow) {
        NovoFont f = font();
        if (f != null) {
            f.draw(s, x, y, argb, shadow);
        } else {
            Minecraft.getMinecraft().fontRendererObj.drawString(s, x, y, argb, shadow);
        }
    }

    /** The end of `s` that fits `maxWidth` (for text fields that scroll). */
    public static String tail(String s, int maxWidth) {
        int start = 0;
        while (start < s.length() && width(s.substring(start)) > maxWidth) {
            start++;
        }
        return s.substring(start);
    }

    public static int width(String s) {
        NovoFont f = font();
        return f != null ? (int) Math.ceil(f.width(s)) : Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
    }

    /** `s` cut (without an ellipsis) to fit `maxWidth`. */
    public static String cut(String s, int maxWidth) {
        NovoFont f = font();
        return f != null ? f.trim(s, maxWidth) : Minecraft.getMinecraft().fontRendererObj.trimStringToWidth(s, maxWidth);
    }

    /** `s` wrapped to lines of at most `maxWidth`. */
    public static java.util.List<String> wrap(String s, int maxWidth) {
        NovoFont f = font();
        return f != null ? f.wrap(s, maxWidth) : Minecraft.getMinecraft().fontRendererObj.listFormattedStringToWidth(s, maxWidth);
    }

    /** `#RRGGBB` (or `#AARRGGBB`) to ARGB; opaque unless alpha is given. */
    public static int parseColor(String hex, int fallback) {
        if (hex == null) {
            return fallback;
        }
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (h.length() == 6) {
                return 0xFF000000 | Integer.parseInt(h, 16);
            }
            if (h.length() == 8) {
                return (int) Long.parseLong(h, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return fallback;
    }

    /** Mixes two ARGB colors; t=0 gives a, t=1 gives b. */
    public static int blend(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Draws a square texture (e.g. a menu icon) smoothly scaled to `size`, tinted `argb`. */
    public static void icon(ResourceLocation texture, float x, float y, float size, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(size / 64f, size / 64f, 1);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 64, 64, 64, 64);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Text with extra space between letters (menu headings). */
    public static void spaced(String s, float x, float y, int argb, float spacing) {
        if (spacing == 0) {
            text(s, x, y, argb);
            return;
        }
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            text(c, cx, y, argb);
            cx += exactWidth(c) + spacing;
        }
    }

    /** Unrounded width, so per-letter layouts don't drift. */
    public static float exactWidth(String s) {
        NovoFont f = font();
        return f != null ? f.width(s) : Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
    }

    public static float spacedWidth(String s, float spacing) {
        return exactWidth(s) + Math.max(0, s.length() - 1) * spacing;
    }

    /** Draws an item icon (with enchant glint) at GUI coordinates. */
    public static void item(ItemStack stack, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /** A filled rectangle with rounded corners, at sub-pixel precision. */
    public static void round(float left, float top, float right, float bottom, float radius, int argb) {
        roundShape(left, top, right, bottom, radius, argb, GL11.GL_POLYGON);
    }

    /** A thin (one screen pixel) rounded outline. */
    public static void roundOutline(float left, float top, float right, float bottom, float radius, int argb) {
        roundShape(left + 0.25f, top + 0.25f, right - 0.25f, bottom - 0.25f, radius, argb, GL11.GL_LINE_LOOP);
    }

    private static void roundShape(float l, float t, float r, float b, float radius, int argb, int mode) {
        radius = Math.max(0, Math.min(radius, Math.min(r - l, b - t) / 2f));
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1f);
        GL11.glBegin(mode);
        // Corners clockwise from top-left; 6 segments per quarter circle.
        float[][] centers = {{l + radius, t + radius}, {r - radius, t + radius}, {r - radius, b - radius}, {l + radius, b - radius}};
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= 6; i++) {
                double a = Math.toRadians(180 + c * 90 + i * 15);
                GL11.glVertex2d(centers[c][0] + Math.cos(a) * radius, centers[c][1] + Math.sin(a) * radius);
            }
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Text drawn at `scale` times the normal size. */
    public static void scaled(String s, float x, float y, int argb, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        text(s, 0, 0, argb);
        GlStateManager.popMatrix();
    }

    /** `s` cut to fit `maxWidth`, with an ellipsis when cut. */
    public static String trim(String s, int maxWidth) {
        if (width(s) <= maxWidth) {
            return s;
        }
        return cut(s, Math.max(0, maxWidth - width("..."))).trim() + "...";
    }
}
