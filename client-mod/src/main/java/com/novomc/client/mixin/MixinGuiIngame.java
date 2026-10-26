package com.novomc.client.mixin;

import com.novomc.client.modules.ScoreboardModule;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.ScoreObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {
    /** Scoreboard: NovoMC draws the sidebar itself (movable, customizable). */
    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
    private void novomc$scoreboard(ScoreObjective objective, ScaledResolution resolution, CallbackInfo ci) {
        if (ScoreboardModule.replacesVanilla()) {
            ci.cancel();
        }
    }
}
