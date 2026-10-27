package com.novomc.client.mixin;

import com.novomc.client.modules.PerformanceTweaksModule;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Performance Tweaks: skip animating water, lava, fire and portal textures. */
@Mixin(TextureMap.class)
public abstract class MixinTextureMap {
    @Inject(method = "updateAnimations", at = @At("HEAD"), cancellable = true)
    private void novomc$noAnimations(CallbackInfo ci) {
        if (PerformanceTweaksModule.noAnimations()) {
            ci.cancel();
        }
    }
}
