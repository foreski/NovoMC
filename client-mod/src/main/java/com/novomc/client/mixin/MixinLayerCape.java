package com.novomc.client.mixin;

import com.novomc.client.render.CapeCloth;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cape physics: the capes the cape layer would draw are drawn as cloth instead.
 *
 * Vanilla's layer is replaced rather than added to, so a player never gets two
 * capes; the cloth places itself with the same transform the layer uses and
 * only the slab at the end differs.
 */
@Mixin(LayerCape.class)
public abstract class MixinLayerCape {
    @Inject(
            method = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void novomc$clothCape(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                  float partialTicks, float ageInTicks, float netHeadYaw, float headPitch,
                                  float scale, CallbackInfo ci) {
        if (CapeCloth.takesOver(player)) {
            CapeCloth.render(player, partialTicks, scale);
            ci.cancel();
        }
    }
}
