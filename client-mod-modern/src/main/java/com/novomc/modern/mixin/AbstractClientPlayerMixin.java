package com.novomc.modern.mixin;

import com.novomc.modern.Cosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Novo Capes: a player's Novo cape replaces their Minecraft one. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void novomc$novoCape(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = Cosmetics.withCape(((AbstractClientPlayer) (Object) this).getUUID(), cir.getReturnValue());
        if (skin != cir.getReturnValue()) {
            cir.setReturnValue(skin);
        }
    }
}
