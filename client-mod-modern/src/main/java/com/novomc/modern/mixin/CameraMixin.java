package com.novomc.modern.mixin;

import com.novomc.modern.Extras;
import com.novomc.modern.Tweaks;
import com.novomc.modern.Visuals;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom narrows the field of view; FOV Changer keeps it steady; Freelook points the camera away from where the player faces. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void novomc$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Tweaks.Zoom.apply(cir.getReturnValue()));
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float novomc$freelookYaw(Entity entity, float partialTick) {
        return Visuals.Freelook.yaw(entity, entity.getViewYRot(partialTick));
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float novomc$freelookPitch(Entity entity, float partialTick) {
        return Visuals.Freelook.pitch(entity, entity.getViewXRot(partialTick));
    }

    @Redirect(method = "tickFov", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getFieldOfViewModifier(ZF)F"))
    private float novomc$fovChanger(AbstractClientPlayer player, boolean firstPerson, float effectScale) {
        return Extras.FovChanger.modifier(player.getFieldOfViewModifier(firstPerson, effectScale));
    }
}
