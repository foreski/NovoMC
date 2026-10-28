package com.novomc.modern.mixin;

import com.novomc.modern.Extras;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hurt Cam: scales the camera tilt when you're hurt. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"), index = 1)
    private float novomc$hurtCam(float degrees) {
        return Extras.HurtCam.scale(degrees);
    }
}
