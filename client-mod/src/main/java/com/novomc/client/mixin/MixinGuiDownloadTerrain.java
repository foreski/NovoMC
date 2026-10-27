package com.novomc.client.mixin;

import com.novomc.client.gui.NovoLoading;
import com.novomc.client.modules.LoadingScreenModule;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loading Screen: "Downloading terrain" in NovoMC's style. */
@Mixin(GuiDownloadTerrain.class)
public abstract class MixinGuiDownloadTerrain extends GuiScreen {
    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void novomc$draw(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (LoadingScreenModule.on()) {
            NovoLoading.draw(width, height, "Joining " + LoadingScreenModule.serverName(), "Loading terrain...", -1);
            ci.cancel();
        }
    }
}
