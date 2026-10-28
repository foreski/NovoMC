package com.novomc.client.mixin;

import com.novomc.client.modules.NovoCapesModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Novo Capes: a player's Novo cape replaces their Minecraft one. */
@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {
    @Inject(method = "getLocationCape", at = @At("HEAD"), cancellable = true)
    private void novomc$novoCape(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation cape = NovoCapesModule.capeOf(((AbstractClientPlayer) (Object) this).getUniqueID());
        if (cape != null) {
            cir.setReturnValue(cape);
        }
    }
}
