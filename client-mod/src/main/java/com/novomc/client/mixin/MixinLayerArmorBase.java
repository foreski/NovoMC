package com.novomc.client.mixin;

import com.novomc.client.modules.GlintModule;
import com.novomc.client.modules.OldAnimationsModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LayerArmorBase.class)
public abstract class MixinLayerArmorBase {
    /** 1.7 Visuals: armor takes the red hurt tint like the body does. */
    @Inject(method = "shouldCombineTextures", at = @At("HEAD"), cancellable = true)
    private void novomc$armorHurtTint(CallbackInfoReturnable<Boolean> cir) {
        if (OldAnimationsModule.armorTint()) {
            cir.setReturnValue(true);
        }
    }

    /** Glint Colorizer: recolors the armor glint (the purple color call). */
    @Redirect(method = "renderGlint", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"))
    private void novomc$glintColor(float r, float g, float b, float a) {
        int c = GlintModule.color();
        if (c != 0 && r != g) {
            GlStateManager.color(((c >> 16) & 255) / 255f * 0.76f, ((c >> 8) & 255) / 255f * 0.76f, (c & 255) / 255f * 0.76f, a);
        } else {
            GlStateManager.color(r, g, b, a);
        }
    }
}
