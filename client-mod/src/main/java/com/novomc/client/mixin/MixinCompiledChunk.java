package com.novomc.client.mixin;

import com.novomc.client.render.AnimatedChunk;
import java.util.Set;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;

/** Smart Animations: remembers which animated textures a built chunk contains. */
@Mixin(CompiledChunk.class)
public abstract class MixinCompiledChunk implements AnimatedChunk {
    private volatile Set<TextureAtlasSprite> novomc$animated;

    @Override
    public Set<TextureAtlasSprite> novomc$getAnimated() {
        return novomc$animated;
    }

    @Override
    public void novomc$setAnimated(Set<TextureAtlasSprite> sprites) {
        novomc$animated = sprites;
    }
}
