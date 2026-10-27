package com.novomc.client.mixin;

import com.novomc.client.gui.NovoTheme;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Menus: scrolling lists (servers, worlds, languages, controls) without the dirt texture. */
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {
    @Shadow public int width;
    @Shadow public int top;
    @Shadow public int bottom;
    @Shadow public int left;
    @Shadow public int right;

    // Forge-added method, so not remapped.
    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void novomc$listBackground(Tessellator tessellator, CallbackInfo ci) {
        if (NovoTheme.enabled) {
            NovoTheme.listBackground(left, top, right, bottom);
            ci.cancel();
        }
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void novomc$bars(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        if (NovoTheme.enabled) {
            NovoTheme.listBar(left, startY, left + width, endY);
            ci.cancel();
        }
    }
}
