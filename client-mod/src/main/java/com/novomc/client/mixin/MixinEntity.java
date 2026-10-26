package com.novomc.client.mixin;

import com.novomc.client.modules.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    /** Freelook: mouse movement turns the camera instead of the player. */
    @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
    private void novomc$freelook(float yaw, float pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getMinecraft().thePlayer && FreelookModule.consumeMouse(yaw, pitch)) {
            ci.cancel();
        }
    }
}
