package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.render.BlockTweaks;
import com.novomc.client.render.ConnectedTextures;
import com.novomc.client.render.CustomSky;
import com.novomc.client.render.FastMath;
import com.novomc.client.render.LazyChunks;
import com.novomc.client.render.SmartAnimations;

/**
 * NovoMC's takes on OptiFine's rendering options (Fast Math, Smart Animations,
 * Lazy Chunk Loading, Connected Textures, Custom Sky, Better Grass, Better
 * Snow). Each is its own module; the work is in the render package.
 */
public class RenderFeatureModule extends Module {
    public RenderFeatureModule(String id) {
        super(id);
    }

    @Override
    protected void onEnable() {
        set(true);
    }

    @Override
    protected void onDisable() {
        set(false);
    }

    @Override
    public void onSettingsChanged() {
        if (id().equals("better_grass")) {
            int mode = "fast".equals(choice("mode")) ? BlockTweaks.GRASS_FAST : BlockTweaks.GRASS_FANCY;
            if (BlockTweaks.betterGrass != mode) {
                BlockTweaks.betterGrass = mode;
                rebuildChunks();
            }
        }
    }

    private void set(boolean on) {
        switch (id()) {
            case "fast_math":
                FastMath.enabled = on;
                break;
            case "smart_animations":
                SmartAnimations.enabled = on;
                // Chunks built while off carry no texture lists yet.
                rebuildChunks();
                break;
            case "lazy_chunks":
                LazyChunks.enabled = on;
                break;
            case "connected_textures":
                ConnectedTextures.setEnabled(on);
                break;
            case "custom_sky":
                CustomSky.enabled = on;
                break;
            case "better_grass":
                if (on) {
                    onSettingsChanged();
                } else if (BlockTweaks.betterGrass != BlockTweaks.GRASS_OFF) {
                    BlockTweaks.betterGrass = BlockTweaks.GRASS_OFF;
                    rebuildChunks();
                }
                break;
            case "better_snow":
                if (BlockTweaks.betterSnow != on) {
                    BlockTweaks.betterSnow = on;
                    rebuildChunks();
                }
                break;
            default:
                throw new IllegalStateException("Unknown render feature " + id());
        }
    }

    private void rebuildChunks() {
        if (mc.renderGlobal != null && mc.theWorld != null) {
            mc.renderGlobal.loadRenderers();
        }
    }
}
