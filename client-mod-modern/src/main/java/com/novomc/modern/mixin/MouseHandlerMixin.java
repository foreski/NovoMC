package com.novomc.modern.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.novomc.modern.Clicks;
import com.novomc.modern.Tweaks;
import com.novomc.modern.Visuals;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CPS counting, the mouse wheel for Zoom, and mouse movement for Freelook. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void novomc$click(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        // Only clicks while playing (mouse captured), not in menus.
        if (action == InputConstants.PRESS && ((MouseHandler) (Object) this).isMouseGrabbed()) {
            Clicks.press(info.button());
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void novomc$zoomScroll(long window, double x, double y, CallbackInfo ci) {
        if (y != 0 && Tweaks.Zoom.scroll(y)) {
            ci.cancel();
        }
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void novomc$freelook(LocalPlayer player, double dx, double dy) {
        if (!Visuals.Freelook.turn(dx, dy)) {
            player.turn(dx, dy);
        }
    }
}
