package com.novomc.client.mixin;

import com.novomc.client.modules.PatcherFixesModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Patcher Fixes: F11 switches to borderless fullscreen instead of exclusive fullscreen. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void novomc$borderless(CallbackInfo ci) {
        if (PatcherFixesModule.borderlessFullscreen()) {
            PatcherFixesModule.toggleBorderless();
            ci.cancel();
        }
    }
}
