package com.novomc.modern.mixin;

import com.novomc.modern.Extras;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: the client world shows the chosen weather; the server's stays underneath. */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void novomc$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
        float level = Extras.WeatherChanger.level(false);
        if (level >= 0 && (Object) this instanceof ClientLevel) {
            cir.setReturnValue(level);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void novomc$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        float level = Extras.WeatherChanger.level(true);
        if (level >= 0 && (Object) this instanceof ClientLevel) {
            cir.setReturnValue(level);
        }
    }
}
