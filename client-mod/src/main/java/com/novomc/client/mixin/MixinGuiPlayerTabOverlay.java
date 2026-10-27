package com.novomc.client.mixin;

import com.novomc.client.modules.NovoBadgeModule;
import com.novomc.client.modules.TabEditorModule;
import net.minecraft.client.gui.FontRenderer;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab Editor: ping numbers, background opacity, hidden header/footer. */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class MixinGuiPlayerTabOverlay {
    private static final int BADGE_ROOM = NovoBadgeModule.SIZE + 1;

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

    // NovoMC Badge: columns widen by the badge for badged players, and the
    // badge is drawn between the head and the name.

    private boolean novomc$widthBadge;
    private boolean novomc$rowBadge;

    @Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;",
            ordinal = 0))
    private String novomc$measuredName(GuiPlayerTabOverlay tab, NetworkPlayerInfo info) {
        novomc$widthBadge = NovoBadgeModule.tabHas(info);
        return tab.getPlayerName(info);
    }

    @Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0))
    private int novomc$nameWidth(FontRenderer font, String text) {
        return font.getStringWidth(text) + (novomc$widthBadge ? BADGE_ROOM : 0);
    }

    @Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;",
            ordinal = 1))
    private String novomc$drawnName(GuiPlayerTabOverlay tab, NetworkPlayerInfo info) {
        novomc$rowBadge = NovoBadgeModule.tabHas(info);
        return tab.getPlayerName(info);
    }

    @Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I", ordinal = 1))
    private int novomc$drawSpectatorName(FontRenderer font, String text, float x, float y, int color) {
        return novomc$name(font, text, x, y, color);
    }

    @Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I", ordinal = 2))
    private int novomc$drawName(FontRenderer font, String text, float x, float y, int color) {
        return novomc$name(font, text, x, y, color);
    }

    private int novomc$name(FontRenderer font, String text, float x, float y, int color) {
        if (!novomc$rowBadge) {
            return font.drawStringWithShadow(text, x, y, color);
        }
        NovoBadgeModule.draw(x, y, 1F);
        return font.drawStringWithShadow(text, x + BADGE_ROOM, y, color);
    }

    @Inject(method = "drawPing", at = @At("HEAD"), cancellable = true)
    private void novomc$pingNumber(int width, int x, int y, NetworkPlayerInfo info, CallbackInfo ci) {
        if (TabEditorModule.pingNumbers()) {
            TabEditorModule.drawPing(width, x, y, info);
            ci.cancel();
        }
    }
}
