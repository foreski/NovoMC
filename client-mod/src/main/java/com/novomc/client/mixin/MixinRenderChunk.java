package com.novomc.client.mixin;

import com.novomc.client.render.AnimatedChunk;
import com.novomc.client.render.SmartAnimations;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smart Animations: collects the animated textures of a chunk while it is built. */
@Mixin(RenderChunk.class)
public abstract class MixinRenderChunk {
    @Inject(method = "rebuildChunk", at = @At("HEAD"))
    private void novomc$beginChunk(float x, float y, float z, ChunkCompileTaskGenerator generator, CallbackInfo ci) {
        SmartAnimations.beginChunk();
    }

    @Inject(method = "rebuildChunk", at = @At("RETURN"))
    private void novomc$endChunk(float x, float y, float z, ChunkCompileTaskGenerator generator, CallbackInfo ci) {
        CompiledChunk compiled = generator.getCompiledChunk();
        if (compiled != null && compiled != CompiledChunk.DUMMY) {
            ((AnimatedChunk) compiled).novomc$setAnimated(SmartAnimations.endChunk());
        } else {
            SmartAnimations.endChunk();
        }
    }
}
