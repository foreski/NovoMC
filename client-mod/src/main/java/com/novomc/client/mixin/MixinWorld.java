package com.novomc.client.mixin;

import com.novomc.client.modules.DynamicLightsModule;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic Lights: entities, your hand and held items light up too. */
@Mixin(World.class)
public abstract class MixinWorld {
    @Inject(method = "getCombinedLight", at = @At("RETURN"), cancellable = true)
    private void novomc$dynamicLight(BlockPos pos, int lightValue, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(DynamicLightsModule.combine(cir.getReturnValue(), pos));
    }
}
