package com.novomc.client.mixin;

import com.novomc.client.sodium.SodiumRenderer;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium renderer: a deleted chunk also frees its region arena space. */
@Mixin(VertexBuffer.class)
public abstract class MixinVertexBuffer {
    @Inject(method = "deleteGlBuffers", at = @At("HEAD"))
    private void novomc$sodiumFree(CallbackInfo ci) {
        SodiumRenderer.free((VertexBuffer) (Object) this);
    }
}
