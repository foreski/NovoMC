package com.novomc.client.mixin;

import com.novomc.client.modules.TabEditorModule;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab Editor: ping numbers, background opacity, hidden header/footer. */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class MixinGuiPlayerTabOverlay {
    @Shadow
    private IChatComponent header;

    @Shadow
    private IChatComponent footer;

    private boolean novomc$hidden;
    private IChatComponent novomc$header;
    private IChatComponent novomc$footer;

    @Inject(method = "renderPlayerlist", at = @At("HEAD"))
    private void novomc$hideHeader(int width, Scoreboard scoreboard, ScoreObjective objective, CallbackInfo ci) {
        if (TabEditorModule.hideHeader()) {
            novomc$header = header;
            novomc$footer = footer;
            header = null;
            footer = null;
            novomc$hidden = true;
        }
    }

    @Inject(method = "renderPlayerlist", at = @At("RETURN"))
    private void novomc$restoreHeader(int width, Scoreboard scoreboard, ScoreObjective objective, CallbackInfo ci) {
        if (novomc$hidden) {
            header = novomc$header;
            footer = novomc$footer;
            novomc$hidden = false;
        }
    }

    /** The list background is drawn in Integer.MIN_VALUE (black at 50%). */
    @ModifyConstant(method = "renderPlayerlist", constant = @Constant(intValue = Integer.MIN_VALUE))
    private int novomc$background(int color) {
        return TabEditorModule.background(color);
    }

    @Inject(method = "drawPing", at = @At("HEAD"), cancellable = true)
    private void novomc$pingNumber(int width, int x, int y, NetworkPlayerInfo info, CallbackInfo ci) {
        if (TabEditorModule.pingNumbers()) {
            TabEditorModule.drawPing(width, x, y, info);
            ci.cancel();
        }
    }
}
