package com.novomc.client.mixin;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.novomc.client.sodium.SodiumRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium renderer: finished chunk meshes go into region arenas instead of a VBO
 * per chunk. Uploads from worker threads are queued by vanilla and come back
 * through here on the client thread.
 */
@Mixin(ChunkRenderDispatcher.class)
public abstract class MixinChunkRenderDispatcher {
    @Inject(method = "uploadChunk", at = @At("HEAD"), cancellable = true)
    private void novomc$sodiumUpload(EnumWorldBlockLayer layer, WorldRenderer worldRenderer, RenderChunk chunk,
                                     CompiledChunk compiled, CallbackInfoReturnable<ListenableFuture<Object>> cir) {
        if (SodiumRenderer.isActive() && Minecraft.getMinecraft().isCallingFromMinecraftThread()
                && SodiumRenderer.upload(layer, worldRenderer, chunk)) {
            worldRenderer.setTranslation(0.0D, 0.0D, 0.0D);
            cir.setReturnValue(Futures.<Object>immediateFuture(null));
        }
    }
}
