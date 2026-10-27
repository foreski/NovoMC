package com.novomc.client.gui;

import com.google.gson.JsonPrimitive;
import com.novomc.client.NovoClient;
import com.novomc.client.config.Catalog;
import com.novomc.client.sodium.SodiumRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.settings.GameSettings;

/**
 * NovoMC's Video Settings: vanilla's video options and NovoMC's rendering
 * options (Sodium Renderer and the OptiFine-style features) on one screen.
 */
public class GuiNovoVideo extends GuiNovoSettings {
    private final int mipmapsAtOpen;

    public GuiNovoVideo(GuiScreen parent) {
        super(parent);
        mipmapsAtOpen = gs.mipmapLevels;
    }

    @Override
    protected String title() {
        return "Video Settings";
    }

    @Override
    protected String subtitle() {
        return "Vanilla options plus Novo Client's Sodium renderer and rendering features";
    }

    @Override
    protected String[][] sections() {
        return new String[][] {{"display", "Display"}, {"quality", "Quality"}, {"performance", "Performance"}};
    }

    @Override
    protected List<Row> build(String id) {
        List<Row> out = new ArrayList<Row>();
        if (id.equals("display")) {
            out.add(option(GameSettings.Options.RENDER_DISTANCE, "Render distance", "How far terrain is drawn"));
            out.add(option(GameSettings.Options.FRAMERATE_LIMIT, "Max framerate", "Unlimited is best for PvP"));
            out.add(option(GameSettings.Options.ENABLE_VSYNC, "VSync", "Syncs to the monitor; adds input delay"));
            out.add(new Row("Fullscreen", "F11 also toggles it") {
                protected Boolean on() {
                    return mc.isFullScreen();
                }

                protected void click() {
                    mc.toggleFullscreen();
                }
            });
            out.add(new Row("GUI scale", "Size of menus and the HUD") {
                protected String value() {
                    return valueOf(GameSettings.Options.GUI_SCALE);
                }

                protected void click() {
                    gs.setOptionValue(GameSettings.Options.GUI_SCALE, 1);
                    ScaledResolution sr = new ScaledResolution(mc);
                    setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
                }
            });
            out.add(option(GameSettings.Options.GAMMA, "Brightness", null));
            out.add(option(GameSettings.Options.VIEW_BOBBING, "View bobbing", null));
        } else if (id.equals("quality")) {
            out.add(option(GameSettings.Options.GRAPHICS, "Graphics", "Fancy draws leaf insides and better water"));
            out.add(option(GameSettings.Options.AMBIENT_OCCLUSION, "Smooth lighting", null));
            out.add(option(GameSettings.Options.RENDER_CLOUDS, "Clouds", null));
            out.add(option(GameSettings.Options.PARTICLES, "Particles", null));
            out.add(option(GameSettings.Options.ENTITY_SHADOWS, "Entity shadows", null));
            out.add(option(GameSettings.Options.MIPMAP_LEVELS, "Mipmap levels", "Smooths distant textures"));
            out.add(module("connected_textures", "Connected textures", "Seamless glass, plus CTM from resource packs"));
            out.add(new Row("Better grass", "Grass covers block sides") {
                protected String value() {
                    if (!NovoClient.config().enabled(Catalog.get("better_grass"))) {
                        return "Off";
                    }
                    com.google.gson.JsonElement e = NovoClient.config().setting(Catalog.get("better_grass"), "mode");
                    return e != null && "fast".equals(e.getAsString()) ? "Fast" : "Fancy";
                }

                protected void click() {
                    String v = value();
                    if (v.equals("Off")) {
                        NovoClient.config().setSetting("better_grass", "mode", new JsonPrimitive("fast"));
                        setModule("better_grass", true);
                    } else if (v.equals("Fast")) {
                        NovoClient.config().setSetting("better_grass", "mode", new JsonPrimitive("fancy"));
                        setModule("better_grass", true);
                    } else {
                        setModule("better_grass", false);
                    }
                }
            });
            out.add(module("better_snow", "Better snow", "Snow around plants and fences"));
            out.add(module("custom_sky", "Custom sky", "Skyboxes from resource packs"));
        } else {
            out.add(module("sodium", "Sodium renderer",
                    SodiumRenderer.isSupported() ? "Draws terrain region by region (port of Sodium)" : "Needs OpenGL 3.3"));
            out.add(module("fast_math", "Fast math", "Smaller, faster sine tables"));
            out.add(module("smart_animations", "Smart animations", "Only animates textures on screen"));
            out.add(module("lazy_chunks", "Lazy chunk loading", "Singleplayer loads chunks more gradually"));
            out.add(option(GameSettings.Options.USE_VBO, "VBOs", "Needed by the Sodium renderer"));
            out.add(module("performance_tweaks", "Performance tweaks", "No clouds or entity shadows, uncapped FPS"));
        }
        return out;
    }

    @Override
    protected void back() {
        applyMipmaps();
        super.back();
    }

    /** Mipmap changes rebuild the block atlas, so they apply once, on leaving. */
    private void applyMipmaps() {
        if (gs.mipmapLevels != mipmapsAtOpen) {
            TextureMap blocks = mc.getTextureMapBlocks();
            blocks.setMipmapLevels(gs.mipmapLevels);
            mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            blocks.setBlurMipmapDirect(false, gs.mipmapLevels > 0);
            mc.scheduleResourcesRefresh();
        }
    }
}
