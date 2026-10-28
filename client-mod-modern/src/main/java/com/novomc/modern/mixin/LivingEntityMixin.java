package com.novomc.modern.mixin;

import com.novomc.modern.Pvp;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem Pop Counter: the server's "protected from death" event is a totem pop; death resets the count. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void novomc$totemPop(byte id, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) {
            return;
        }
        if (id == EntityEvent.PROTECTED_FROM_DEATH) {
            Pvp.TotemPops.popped(self);
        } else if (id == EntityEvent.DEATH) {
            Pvp.TotemPops.died(self);
        }
    }
}
