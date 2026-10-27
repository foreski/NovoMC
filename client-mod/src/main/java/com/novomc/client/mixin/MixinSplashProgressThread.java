package com.novomc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Forge's startup splash tints the logo and the spinning texture with the
 * background colour. That's invisible against NovoMC's dark theme, so draw
 * them untinted. Both setColor calls in run() are those two (Forge 1.8.9).
 */
@Mixin(targets = "net.minecraftforge.fml.client.SplashProgress$3", remap = false)
public abstract class MixinSplashProgressThread {
    @ModifyArg(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/client/SplashProgress$3;setColor(I)V"), require = 0)
    private int novomc$untinted(int color) {
        return 0xFFFFFF;
    }
}
