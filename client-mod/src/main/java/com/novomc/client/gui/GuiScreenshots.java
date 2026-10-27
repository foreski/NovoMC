package com.novomc.client.gui;

import com.novomc.client.modules.ScreenshotsModule;
import com.novomc.client.util.Draw;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Gallery of the instance's screenshots: click one to view it large, then open, copy or delete it. */
public class GuiScreenshots extends NovoPanelScreen {
    private static final int THUMB_W = 320;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NovoMC screenshot thumbnails");
            t.setDaemon(true);
            return t;
        }
    });
    /** Thumbnails by "name:lastModified"; {texture, width, height}. */
    private static final Map<String, Object[]> THUMBS = new HashMap<String, Object[]>();
    private static final Set<String> LOADING = new HashSet<String>();

    private List<File> files;
    private File selected;
    private String status;

    public GuiScreenshots(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected String tab() {
        return "screenshots";
    }

    @Override
    public void initGui() {
        super.initGui();
        files = ScreenshotsModule.list();
    }

    private static String key(File f) {
        return f.getName() + ":" + f.lastModified();
    }

    /** The thumbnail for `f`, loading it in the background the first time. */
    private Object[] thumb(final File f) {
        final String key = key(f);
        Object[] t = THUMBS.get(key);
        if (t == null && LOADING.add(key)) {
            LOADER.submit(new Runnable() {
                public void run() {
                    try {
                        BufferedImage src = ImageIO.read(f);
                        if (src == null) {
                            return;
                        }
                        int h = Math.max(1, src.getHeight() * THUMB_W / src.getWidth());
                        final BufferedImage small = new BufferedImage(THUMB_W, h, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = small.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(src, 0, 0, THUMB_W, h, null);
                        g.dispose();
                        mc.addScheduledTask(new Runnable() {
                            public void run() {
                                ResourceLocation loc = mc.getTextureManager().getDynamicTextureLocation("novomc_shot", new DynamicTexture(small));
                                THUMBS.put(key, new Object[] {loc, small.getWidth(), small.getHeight()});
                            }
                        });
                    } catch (Exception ignored) {
                        // unreadable file: it just has no thumbnail
                    }
                }
            });
        }
        return t;
    }

    private void image(ResourceLocation loc, float x, float y, float w, float h) {
        mc.getTextureManager().bindTexture(loc);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(w / 100f, h / 100f, 1);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 100, 100, 100, 100);
        GlStateManager.popMatrix();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int[] p = beginFrame(mouseX, mouseY);
        int l = p[0], top = p[1], r = p[2], bottom = p[3] - PAD;
        drawHeader(l, r, top, "Screenshots", files.size() + " in this instance · press F2 in game to take one");
        if (selected != null) {
            drawPreview(l, top + 58, r, bottom);
        } else {
            drawGrid(l, top + 58, r, bottom);
        }
        if (status != null) {
            Draw.text(status, l, bottom - 2, GREEN);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawGrid(int l, int top, int r, int bottom) {
        int cols = r - l >= 460 ? 4 : 3;
        int gap = 6;
        float w = (r - l - gap * (cols - 1)) / (float) cols;
        float h = w * 9 / 16;
        int rows = (files.size() + cols - 1) / cols;
        float max = updateScroll(rows * (h + gap), bottom - top);
        clip(l - 1, top, r + 1, bottom);
        for (int i = 0; i < files.size(); i++) {
            final File f = files.get(i);
            float x = l + (i % cols) * (w + gap);
            float y = top - scroll + (i / cols) * (h + gap);
            if (y + h < top || y > bottom) {
                continue;
            }
            boolean hover = in(x, y, x + w, y + h);
            Draw.round(x, y, x + w, y + h, 4, FIELD);
            Object[] t = thumb(f);
            if (t != null) {
                image((ResourceLocation) t[0], x, y, w, h);
            }
            Draw.roundOutline(x, y, x + w, y + h, 4, hover ? ACCENT : 0x22FFFFFF);
            hit(x, y, x + w, y + h, new Runnable() {
                public void run() {
                    selected = f;
                    status = null;
                }
            });
        }
        noClip();
        if (files.isEmpty()) {
            String hint = "No screenshots yet.";
            Draw.text(hint, (l + r - Draw.width(hint)) / 2f, top + 30, MUTED);
        }
        drawScrollbar(r, top, bottom, max);
    }

    private void drawPreview(int l, int top, int r, int bottom) {
        final File f = selected;
        Object[] t = thumb(f);
        float maxW = r - l, maxH = bottom - top - 26;
        float w = maxW, h = maxW * 9 / 16;
        if (t != null) {
            h = maxW * (Integer) t[2] / (float) (Integer) t[1];
        }
        if (h > maxH) {
            w = w * maxH / h;
            h = maxH;
        }
        float x = l + (maxW - w) / 2;
        Draw.round(x, top, x + w, top + h, 4, FIELD);
        if (t != null) {
            image((ResourceLocation) t[0], x, top, w, h);
        }
        float by = top + h + 8;
        Draw.text(Draw.trim(f.getName(), r - l - 230), l, by + 2, MUTED);
        float bx = r;
        bx = button(bx, by, "Back", null, new Runnable() {
            public void run() {
                selected = null;
            }
        });
        bx = button(bx, by, "Delete", null, new Runnable() {
            public void run() {
                if (f.delete()) {
                    files = ScreenshotsModule.list();
                    selected = null;
                    status = "Deleted " + f.getName();
                }
            }
        });
        bx = button(bx, by, "Copy", null, new Runnable() {
            public void run() {
                status = ScreenshotsModule.copy(f) ? "Copied to the clipboard" : "Couldn't copy the image";
            }
        });
        button(bx, by, "Open", null, new Runnable() {
            public void run() {
                ScreenshotsModule.open(f);
            }
        });
    }

    @Override
    protected void keyTyped(char c, int keyCode) throws java.io.IOException {
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE && selected != null) {
            selected = null;
            return;
        }
        super.keyTyped(c, keyCode);
    }
}
