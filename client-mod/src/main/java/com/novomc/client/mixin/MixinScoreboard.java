package com.novomc.client.mixin;

import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla 1.8.9 crashes its packet handler when a server (Hypixel, often)
 * removes a scoreboard objective or team the client never had, or a player
 * from a team they aren't on. Each crash logs a stack trace and drops the
 * rest of that packet's work; these guards make those removals no-ops.
 */
@Mixin(Scoreboard.class)
public abstract class MixinScoreboard {
    @Shadow
    public abstract ScorePlayerTeam getPlayersTeam(String name);

    @Inject(method = "removeObjective", at = @At("HEAD"), cancellable = true)
    private void novomc$unknownObjective(ScoreObjective objective, CallbackInfo ci) {
        if (objective == null) {
            ci.cancel();
        }
    }

    @Inject(method = "removeTeam", at = @At("HEAD"), cancellable = true)
    private void novomc$unknownTeam(ScorePlayerTeam team, CallbackInfo ci) {
        if (team == null) {
            ci.cancel();
        }
    }

    @Inject(method = "removePlayerFromTeam", at = @At("HEAD"), cancellable = true)
    private void novomc$notOnTeam(String player, ScorePlayerTeam team, CallbackInfo ci) {
        if (team == null || getPlayersTeam(player) != team) {
            ci.cancel();
        }
    }
}
