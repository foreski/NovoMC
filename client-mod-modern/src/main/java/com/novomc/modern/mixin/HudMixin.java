package com.novomc.modern.mixin;

import com.novomc.modern.More;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scoreboard: the sidebar's background, red numbers and text shadow. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I"))
    private int novomc$sidebarBackground(Options options, float opacity) {
        More.Scoreboard s = More.Scoreboard.active();
        // Vanilla asks 0.4 for the title row and 0.3 for the lines.
        return s != null ? s.background(opacity > 0.35f) : options.getBackgroundColor(opacity);
    }

    @Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"))
    private NumberFormat novomc$sidebarNumbers(Objective objective, NumberFormat fallback) {
        More.Scoreboard s = More.Scoreboard.active();
        return s != null && !s.numbers() ? BlankFormat.INSTANCE : objective.numberFormatOrDefault(fallback);
    }

    @ModifyArg(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"), index = 5)
    private boolean novomc$sidebarShadow(boolean shadow) {
        More.Scoreboard s = More.Scoreboard.active();
        return s != null ? s.shadow() : shadow;
    }
}
