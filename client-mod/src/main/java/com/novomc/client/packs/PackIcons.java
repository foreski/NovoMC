package com.novomc.client.packs;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * Modrinth pack icons, fetched in the background and turned into small
 * textures. Modrinth serves PNG and (mostly) WebP; Java 8 only reads PNG, so
 * WebP goes through TwelveMonkeys' pure-Java decoder.
 */
public final class PackIcons {
    private static final int SIZE = 64;
    private static final int MAX_ICONS = 300;
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(4, PackLibrary.daemon("NovoMC pack icons"));
    /** url -> texture; a null value means loading or failed. Least recently used first. */
    private static final Map<String, ResourceLocation> ICONS = new LinkedHashMap<String, ResourceLocation>(64, 0.75f, true);

    private PackIcons() {}

    /** The icon for `url`, or null while it loads (or if it can't be shown). Call on the render thread. */
    public static ResourceLocation get(final String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        if (ICONS.containsKey(url)) {
            return ICONS.get(url);
        }
        ICONS.put(url, null);
        evict();
        LOADER.submit(new Runnable() {
            public void run() {
                final BufferedImage image = load(url);
                if (image == null) {
                    return;
                }
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    public void run() {
                        if (ICONS.containsKey(url)) {
                            DynamicTexture tex = new DynamicTexture(image);
                            ICONS.put(url, Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("novomc_pack", tex));
                        }
                    }
                });
            }
        });
        return null;
    }

    private static void evict() {
        while (ICONS.size() > MAX_ICONS) {
            String oldest = ICONS.keySet().iterator().next();
            ResourceLocation loc = ICONS.remove(oldest);
            if (loc != null) {
                Minecraft.getMinecraft().getTextureManager().deleteTexture(loc);
            }
        }
    }

    private static BufferedImage load(String url) {
        try {
            byte[] bytes = Modrinth.bytes(url, 4 << 20);
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) {
                src = webp(bytes);
            }
            return src == null ? null : scaled(src);
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage webp(byte[] bytes) throws Exception {
        ImageReader reader = new WebPImageReaderSpi().createReaderInstance();
        ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
        try {
            reader.setInput(in);
            return reader.read(0);
        } finally {
            reader.dispose();
            in.close();
        }
    }

    /** Square SIZE x SIZE ARGB, fitted and centered. */
    private static BufferedImage scaled(BufferedImage src) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // Pixel-art icons stay crisp when enlarged; big ones are smoothed down.
        boolean upscale = src.getWidth() < SIZE && src.getHeight() < SIZE;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                upscale ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        float s = Math.min(SIZE / (float) src.getWidth(), SIZE / (float) src.getHeight());
        int w = Math.max(1, Math.round(src.getWidth() * s));
        int h = Math.max(1, Math.round(src.getHeight() * s));
        g.drawImage(src, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
        g.dispose();
        return out;
    }
}
