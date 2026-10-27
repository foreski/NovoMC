package com.novomc.client.mixin;

import com.novomc.client.gui.NovoLoading;
import com.novomc.client.modules.LoadingScreenModule;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loading Screen: "Connecting to server" in NovoMC's style (the Cancel button still works). */
@Mixin(GuiConnecting.class)
public abstract class MixinGuiConnecting extends GuiScreen {
    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void novomc$draw(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!LoadingScreenModule.on()) {
            return;
        }
        NovoLoading.draw(width, height, "Connecting to " + LoadingScreenModule.serverName(), "Logging in...", -1);
        for (GuiButton b : buttonList) {
            b.drawButton(mc, mouseX, mouseY);
        }
        ci.cancel();
    }
}
