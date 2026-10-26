package com.novomc.client.mixin;

import com.novomc.client.modules.ItemRendering;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderEntityItem.class)
public abstract class MixinRenderEntityItem {
    /** 2D Items and Item Physics draw dropped items themselves. */
    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void novomc$renderItem(EntityItem entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (ItemRendering.render(entity, x, y, z, partialTicks)) {
            ci.cancel();
        }
    }
}
