package com.novomc.client.render;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Smart Animations, inspired by OptiFine's option of the same name; NovoMC's own
 * code. Each built chunk remembers which animated block textures it contains;
 * every tick only the textures in chunks drawn since the last tick are animated.
 * Textures never seen on a block (items, entity fire, the portal overlay) always
 * animate.
 */
public final class SmartAnimations {
    public static volatile boolean enabled;

    /** Animated textures that have been seen on blocks at least once. */
    private static final Set<TextureAtlasSprite> BLOCK_SPRITES =
            Collections.newSetFromMap(new ConcurrentHashMap<TextureAtlasSprite, Boolean>());
    /** Textures in chunks drawn since the last animation tick (client thread only). */
    private static Set<TextureAtlasSprite> visible = Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());
    private static Set<TextureAtlasSprite> visibleNext = Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());

    /** The chunk being built on this thread collects its animated textures here. */
    private static final ThreadLocal<Set<TextureAtlasSprite>> BUILDING = new ThreadLocal<Set<TextureAtlasSprite>>();

    private SmartAnimations() {}

    public static void beginChunk() {
        BUILDING.set(Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>()));
    }

    /** The finished chunk's animated textures, or null when it has none. */
    public static Set<TextureAtlasSprite> endChunk() {
        Set<TextureAtlasSprite> set = BUILDING.get();
        BUILDING.remove();
        return set == null || set.isEmpty() ? null : set;
    }

    /** Called for every block put into the chunk being built. */
    public static void block(IBlockState state) {
        Set<TextureAtlasSprite> set = BUILDING.get();
        if (set == null) {
            return;
        }
        Material m = state.getBlock().getMaterial();
        Minecraft mc = Minecraft.getMinecraft();
        if (m == Material.water || m == Material.lava) {
            String fluid = m == Material.water ? "water" : "lava";
            add(set, mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/" + fluid + "_still"));
            add(set, mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/" + fluid + "_flow"));
        } else {
            add(set, mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(state));
        }
    }

    private static void add(Set<TextureAtlasSprite> set, TextureAtlasSprite s) {
        if (s != null && s.hasAnimationMetadata()) {
            String name = s.getIconName();
            // Also drawn on burning entities and the nether portal overlay.
            if (!name.contains("fire_layer") && !name.endsWith("portal")) {
                set.add(s);
                BLOCK_SPRITES.add(s);
            }
        }
    }

    /** A chunk was queued for drawing this frame. */
    public static void drawn(Set<TextureAtlasSprite> sprites) {
        if (sprites != null) {
            visibleNext.addAll(sprites);
        }
    }

    /** Start of an animation tick: freeze the set of textures seen since the last one. */
    public static void tick() {
        Set<TextureAtlasSprite> t = visible;
        visible = visibleNext;
        visibleNext = t;
        visibleNext.clear();
    }

    public static boolean shouldAnimate(TextureAtlasSprite sprite) {
        return !enabled || !BLOCK_SPRITES.contains(sprite) || visible.contains(sprite);
    }
}
