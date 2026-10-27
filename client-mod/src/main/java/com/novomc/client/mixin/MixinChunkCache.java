package com.novomc.client.mixin;

import com.novomc.client.modules.DynamicLightsModule;
import net.minecraft.util.BlockPos;
import net.minecraft.world.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic Lights: blocks light up while their chunk is built. */
@Mixin(ChunkCache.class)
public abstract class MixinChunkCache {
    @Inject(method = "getCombinedLight", at = @At("RETURN"), cancellable = true)
    private void novomc$dynamicLight(BlockPos pos, int lightValue, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(DynamicLightsModule.combine(cir.getReturnValue(), pos));
    }
}
