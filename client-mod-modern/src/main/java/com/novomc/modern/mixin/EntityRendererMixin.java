package com.novomc.modern.mixin;

import com.novomc.modern.Pvp;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Totem Pop Counter: "-N" after a player's nametag. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void novomc$pops(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component tag = Pvp.TotemPops.nametag(entity, cir.getReturnValue());
        if (tag != cir.getReturnValue()) {
            cir.setReturnValue(tag);
        }
    }
}
