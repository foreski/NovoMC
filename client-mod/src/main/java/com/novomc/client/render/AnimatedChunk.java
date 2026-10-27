package com.novomc.client.render;

import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Added to CompiledChunk: the animated block textures in the chunk (Smart Animations). */
public interface AnimatedChunk {
    Set<TextureAtlasSprite> novomc$getAnimated();

    void novomc$setAnimated(Set<TextureAtlasSprite> sprites);
}
