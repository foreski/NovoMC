package com.novomc.client.mixin;

import com.novomc.client.modules.PerformanceTweaksModule;
import com.novomc.client.render.SmartAnimations;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Performance Tweaks: skip animating water, lava, fire and portal textures.
 * Smart Animations: only animate block textures that are on screen.
 */
@Mixin(TextureMap.class)
public abstract class MixinTextureMap {
    @Inject(method = "updateAnimations", at = @At("HEAD"), cancellable = true)
    private void novomc$noAnimations(CallbackInfo ci) {
        if (PerformanceTweaksModule.noAnimations()) {
            ci.cancel();
        } else {
            SmartAnimations.tick();
        }
    }

    @Redirect(method = "updateAnimations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;updateAnimation()V"))
    private void novomc$smartAnimation(TextureAtlasSprite sprite) {
        if (SmartAnimations.shouldAnimate(sprite)) {
            sprite.updateAnimation();
        }
    }
}
