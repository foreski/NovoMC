package com.novomc.client.mixin;

import com.novomc.client.modules.TitlesModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Titles: scales (around the screen center) or hides title/subtitle text. */
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class MixinGuiIngameForge {
    private boolean novomc$scaled;

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
    private void novomc$titleStart(int width, int height, float partialTicks, CallbackInfo ci) {
        if (TitlesModule.hide()) {
            ci.cancel();
            return;
        }
        float s = TitlesModule.scale();
        if (s != 1f) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(width / 2f, height / 2f, 0);
            GlStateManager.scale(s, s, 1);
            GlStateManager.translate(-width / 2f, -height / 2f, 0);
            novomc$scaled = true;
        }
    }

    @Inject(method = "renderTitle", at = @At("RETURN"))
    private void novomc$titleEnd(int width, int height, float partialTicks, CallbackInfo ci) {
        if (novomc$scaled) {
            GlStateManager.popMatrix();
            novomc$scaled = false;
        }
    }
}
