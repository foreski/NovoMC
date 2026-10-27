package com.novomc.client.mixin;

import com.novomc.client.render.AnimatedChunk;
import com.novomc.client.render.SmartAnimations;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smart Animations: chunks queued for drawing mark their animated textures as visible. */
@Mixin(ChunkRenderContainer.class)
public abstract class MixinChunkRenderContainer {
    @Inject(method = "addRenderChunk", at = @At("HEAD"))
    private void novomc$visible(RenderChunk chunk, EnumWorldBlockLayer layer, CallbackInfo ci) {
        if (SmartAnimations.enabled) {
            SmartAnimations.drawn(((AnimatedChunk) chunk.getCompiledChunk()).novomc$getAnimated());
        }
    }
}
