package com.novomc.client.render;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * Custom Sky: skybox layers from resource packs in the OptiFine / MCPatcher
 * "Better Skies" format ({@code assets/minecraft/optifine/sky/world0/skyN.properties}
 * or {@code mcpatcher/sky}). NovoMC's own implementation of that format.
 */
public final class CustomSky implements IResourceManagerReloadListener {
    private static final Logger LOG = LogManager.getLogger("NovoMC Sky");
    private static final double DISTANCE = 100.0;

    public static volatile boolean enabled;
    /** Layers per world: index 0 overworld, 1 the End. */
    private static final List<List<Layer>> WORLDS = new ArrayList<List<Layer>>();

    private static final class Layer {
        ResourceLocation texture;
        int fadeInStart = -1, fadeInEnd, fadeOutEnd;
        String blend = "add";
        boolean rotate = true;
        float speed = 1;
        float[] axis = {0, 0, 1};
        boolean clear = true, rain, thunder;
        int daysLoop = 8;
        boolean[] days;

        float brightness(WorldClient world, float partialTicks) {
            int time = (int) (world.getWorldTime() % 24000L);
            float b = 1;
            if (fadeInStart >= 0) {
                int fadeOutStart = fadeOutEnd - (fadeInEnd - fadeInStart);
                b = fade(time, fadeInStart, fadeInEnd, fadeOutStart, fadeOutEnd);
            }
            if (days != null) {
                int day = (int) (world.getWorldTime() / 24000L % daysLoop);
                if (day < days.length && !days[day]) {
                    return 0;
                }
            }
            float rain = world.getRainStrength(partialTicks);
            float thunder = world.getThunderStrength(partialTicks);
            float weather = (clear ? 1 - rain : 0) + (this.rain ? rain - thunder : 0) + (this.thunder ? thunder : 0);
            return b * Math.max(0, Math.min(1, weather));
        }

        private static float fade(int t, int inStart, int inEnd, int outStart, int outEnd) {
            if (within(t, inStart, inEnd)) {
                return span(inStart, t) / (float) Math.max(1, span(inStart, inEnd));
            } else if (within(t, inEnd, outStart)) {
                return 1;
            } else if (within(t, outStart, outEnd)) {
                return 1 - span(outStart, t) / (float) Math.max(1, span(outStart, outEnd));
            }
            return 0;
        }

        private static int span(int from, int to) {
            return ((to - from) % 24000 + 24000) % 24000;
        }

        private static boolean within(int t, int from, int to) {
            return span(from, t) <= span(from, to);
        }
    }

    // ---- Loading ----

    @Override
    public void onResourceManagerReload(IResourceManager rm) {
        WORLDS.clear();
        for (int w = 0; w <= 1; w++) {
            List<Layer> layers = new ArrayList<Layer>();
            for (String root : new String[] {"optifine", "mcpatcher"}) {
                String dir = root + "/sky/world" + w;
                int misses = 0;
                for (int n = 0; misses < 2 && n < 100; n++) {
                    Layer layer = load(rm, dir, n);
                    if (layer == null) {
                        misses++;
                    } else {
                        misses = 0;
                        layers.add(layer);
                    }
                }
                if (!layers.isEmpty()) {
                    break;
                }
            }
            WORLDS.add(layers);
            if (!layers.isEmpty()) {
                LOG.info("Loaded {} custom sky layers for world{}", layers.size(), w);
            }
        }
    }

    private static Layer load(IResourceManager rm, String dir, int n) {
        Properties p = new Properties();
        try (InputStream in = rm.getResource(new ResourceLocation("minecraft", dir + "/sky" + n + ".properties")).getInputStream()) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        Layer l = new Layer();
        l.texture = texture(p.getProperty("source", "./sky" + n + ".png").trim(), dir);
        try {
            if (p.getProperty("startFadeIn") != null && p.getProperty("endFadeIn") != null && p.getProperty("endFadeOut") != null) {
                l.fadeInStart = ticks(p.getProperty("startFadeIn"));
                l.fadeInEnd = ticks(p.getProperty("endFadeIn"));
                l.fadeOutEnd = ticks(p.getProperty("endFadeOut"));
            }
            l.blend = p.getProperty("blend", "add").trim().toLowerCase();
            l.rotate = Boolean.parseBoolean(p.getProperty("rotate", "true").trim());
            l.speed = Float.parseFloat(p.getProperty("speed", "1").trim());
            String[] axis = p.getProperty("axis", "0 0 1").trim().split("\\s+");
            l.axis = new float[] {Float.parseFloat(axis[0]), Float.parseFloat(axis[1]), Float.parseFloat(axis[2])};
            String weather = p.getProperty("weather", "clear");
            l.clear = weather.contains("clear");
            l.rain = weather.contains("rain");
            l.thunder = weather.contains("thunder");
            l.daysLoop = Math.max(1, Integer.parseInt(p.getProperty("daysLoop", "8").trim()));
            String days = p.getProperty("days");
            if (days != null) {
                l.days = new boolean[l.daysLoop];
                for (String d : days.trim().split("\\s+")) {
                    String[] range = d.split("-");
                    int from = Integer.parseInt(range[0]);
                    int to = range.length > 1 ? Integer.parseInt(range[1]) : from;
                    for (int i = from; i <= to && i < l.daysLoop; i++) {
                        l.days[i] = true;
                    }
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Bad custom sky {}/sky{}.properties: {}", dir, n, e.toString());
            return null;
        }
        return l;
    }

    /** Resolves a texture path as the format defines: ./relative, ~/optifine, namespace:path or assets/minecraft. */
    private static ResourceLocation texture(String path, String dir) {
        if (path.startsWith("./")) {
            return new ResourceLocation("minecraft", dir + path.substring(1));
        } else if (path.startsWith("~/")) {
            return new ResourceLocation("minecraft", "optifine/" + path.substring(2));
        } else if (path.contains(":")) {
            return new ResourceLocation(path);
        }
        return new ResourceLocation("minecraft", path);
    }

    /** "hh:mm" to world time, where 6:00 is 0. */
    private static int ticks(String hhmm) {
        String[] parts = hhmm.trim().split(":");
        int minutes = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        return ((minutes - 360) * 1000 / 60 % 24000 + 24000) % 24000;
    }

    // ---- Drawing ----

    /** Draws the custom layers over the vanilla sky colour, before the sun and moon. */
    public static void render(WorldClient world, float partialTicks) {
        int index = world.provider.getDimensionId() == 1 ? 1 : world.provider.isSurfaceWorld() ? 0 : -1;
        if (!enabled || index < 0 || index >= WORLDS.size() || WORLDS.get(index).isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        float celestial = world.getCelestialAngle(partialTicks);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableFog();
        GlStateManager.depthMask(false);
        for (Layer l : WORLDS.get(index)) {
            float b = l.brightness(world, partialTicks);
            if (b <= 0.0001f) {
                continue;
            }
            mc.getTextureManager().bindTexture(l.texture);
            blend(l.blend, b);
            GlStateManager.pushMatrix();
            if (l.rotate) {
                GlStateManager.rotate(celestial * 360f * l.speed, l.axis[0], l.axis[1], l.axis[2]);
            }
            drawBox();
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
    }

    private static void blend(String method, float b) {
        int src, dst;
        boolean alphaOnly = true;
        switch (method) {
            case "subtract": src = GL11.GL_ONE_MINUS_DST_COLOR; dst = GL11.GL_ZERO; alphaOnly = false; break;
            case "multiply": src = GL11.GL_DST_COLOR; dst = GL11.GL_ONE_MINUS_SRC_ALPHA; GlStateManager.color(b, b, b, b);
                GlStateManager.blendFunc(src, dst); return;
            case "dodge": src = GL11.GL_ONE; dst = GL11.GL_ONE; alphaOnly = false; break;
            case "burn": src = GL11.GL_ZERO; dst = GL11.GL_ONE_MINUS_SRC_COLOR; alphaOnly = false; break;
            case "screen": src = GL11.GL_ONE; dst = GL11.GL_ONE_MINUS_SRC_COLOR; alphaOnly = false; break;
            case "overlay": src = GL11.GL_DST_COLOR; dst = GL11.GL_SRC_COLOR; alphaOnly = false; break;
            case "replace": src = GL11.GL_ONE; dst = GL11.GL_ZERO; break;
            case "alpha": src = GL11.GL_SRC_ALPHA; dst = GL11.GL_ONE_MINUS_SRC_ALPHA; break;
            default: src = GL11.GL_SRC_ALPHA; dst = GL11.GL_ONE; break;
        }
        GlStateManager.blendFunc(src, dst);
        if (alphaOnly) {
            GlStateManager.color(1, 1, 1, b);
        } else {
            GlStateManager.color(b, b, b, 1);
        }
    }

    /**
     * The format's cube: a 3x2 texture whose cells 0-5 are bottom, top, south, east,
     * north and west, each face turned the way Better Skies packs expect.
     */
    private static void drawBox() {
        GlStateManager.rotate(90, 1, 0, 0);
        GlStateManager.rotate(-90, 0, 0, 1);
        face(4); // north
        GlStateManager.pushMatrix();
        GlStateManager.rotate(90, 1, 0, 0);
        face(1); // top
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.rotate(-90, 1, 0, 0);
        face(0); // bottom
        GlStateManager.popMatrix();
        GlStateManager.rotate(90, 0, 0, 1);
        face(5); // west
        GlStateManager.rotate(90, 0, 0, 1);
        face(2); // south
        GlStateManager.rotate(90, 0, 0, 1);
        face(3); // east
    }

    private static void face(int cell) {
        double u = (cell % 3) / 3.0;
        double v = (cell / 3) / 2.0;
        double d = DISTANCE;
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(-d, -d, -d).tex(u, v).endVertex();
        wr.pos(-d, -d, d).tex(u, v + 0.5).endVertex();
        wr.pos(d, -d, d).tex(u + 1 / 3.0, v + 0.5).endVertex();
        wr.pos(d, -d, -d).tex(u + 1 / 3.0, v).endVertex();
        t.draw();
    }
}
