package com.novomc.client.mixin;

import com.novomc.client.modules.EntityCullingModule;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity Culling: far entities fail the visibility check, so nothing of them is drawn. */
@Mixin(RenderManager.class)
public abstract class MixinRenderManager {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void novomc$cullFar(Entity entity, ICamera camera, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (EntityCullingModule.cull(entity, x, y, z)) {
            cir.setReturnValue(false);
        }
    }
}
