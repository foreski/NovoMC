package com.novomc.client.mixin;

import com.novomc.client.modules.FreelookModule;
import com.novomc.client.modules.HurtCamModule;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    /** Hurt Cam: scales the 14-degree damage tilt. */
    @ModifyConstant(method = "hurtCameraEffect", constant = @Constant(floatValue = 14.0F))
    private float novomc$hurtCam(float degrees) {
        return HurtCamModule.scale(degrees);
    }

    // Freelook: the camera reads these angles; the player keeps its own.

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F"))
    private float novomc$yaw(Entity entity) {
        return FreelookModule.yaw(entity, entity.rotationYaw);
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F"))
    private float novomc$prevYaw(Entity entity) {
        return FreelookModule.yaw(entity, entity.prevRotationYaw);
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F"))
    private float novomc$pitch(Entity entity) {
        return FreelookModule.pitch(entity, entity.rotationPitch);
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F"))
    private float novomc$prevPitch(Entity entity) {
        return FreelookModule.pitch(entity, entity.prevRotationPitch);
    }
}
