package com.novomc.modern.mixin;

import com.novomc.modern.More;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: your own nametag in third person. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
    private void novomc$showOwn(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && More.Nametags.showOwn(entity)) {
            cir.setReturnValue(true);
        }
    }
}
