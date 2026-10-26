package com.novomc.client.mixin;

import com.novomc.client.modules.EntityCullingModule;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity Culling: skips far chests, signs, skulls and banners (vanilla draws them up to 64 blocks). */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcher {
    @Inject(method = "renderTileEntity", at = @At("HEAD"), cancellable = true)
    private void novomc$cullFar(TileEntity te, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (EntityCullingModule.cull(te, TileEntityRendererDispatcher.staticPlayerX, TileEntityRendererDispatcher.staticPlayerY,
                TileEntityRendererDispatcher.staticPlayerZ)) {
            ci.cancel();
        }
    }
}
