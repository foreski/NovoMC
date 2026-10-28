package com.novomc.modern.mixin;

import com.novomc.modern.Pvp;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab Editor: ping numbers, background opacity, and hiding the server's header and footer. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
    private void novomc$ping(GuiGraphicsExtractor g, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
        Pvp.TabEditor t = Pvp.TabEditor.active();
        if (t != null && t.pingNumbers()) {
            Pvp.TabEditor.drawPing(g, width, x, y, info);
            ci.cancel();
        }
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4)
    private int novomc$background(int color) {
        Pvp.TabEditor t = Pvp.TabEditor.active();
        // Vanilla's panel backgrounds are half-transparent black.
        return t != null && color == 0x80000000 ? t.background() : color;
    }

    @Redirect(method = "extractRenderState", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;"))
    private Component novomc$header(PlayerTabOverlay self) {
        Pvp.TabEditor t = Pvp.TabEditor.active();
        return t != null && t.hideHeader() ? null : ((PlayerTabOverlayAccessor) self).novomc$header();
    }

    @Redirect(method = "extractRenderState", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;"))
    private Component novomc$footer(PlayerTabOverlay self) {
        Pvp.TabEditor t = Pvp.TabEditor.active();
        return t != null && t.hideHeader() ? null : ((PlayerTabOverlayAccessor) self).novomc$footer();
    }
}
