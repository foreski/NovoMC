package com.novomc.client.mixin;

import com.novomc.client.mixin.accessor.ChunkRenderContainerAccessor;
import com.novomc.client.sodium.SodiumRenderer;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium renderer: draws a terrain layer region by region instead of chunk by chunk. */
@Mixin(VboRenderList.class)
public abstract class MixinVboRenderList extends ChunkRenderContainer {
    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void novomc$sodiumRender(EnumWorldBlockLayer layer, CallbackInfo ci) {
        if (SodiumRenderer.isActive()) {
            if (initialized) {
                ChunkRenderContainerAccessor view = (ChunkRenderContainerAccessor) this;
                SodiumRenderer.render(layer, renderChunks, view.novomc$getViewX(), view.novomc$getViewY(), view.novomc$getViewZ());
                renderChunks.clear();
            }
            ci.cancel();
        }
    }
}
