package com.novomc.client.mixin;

import com.novomc.client.modules.ScrollableTooltipsModule;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scrollable Tooltips: shifts the tooltip by the scroll offset. Targets
 * Forge's FontRenderer overload, which every item tooltip goes through
 * (Forge-added, so not remapped).
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {
    private boolean novomc$shifted;

    @Inject(method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V", at = @At("HEAD"), remap = false)
    private void novomc$scrollStart(List<String> lines, int x, int y, FontRenderer font, CallbackInfo ci) {
        int offset = ScrollableTooltipsModule.offsetFor(lines);
        if (offset != 0) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, offset, 0);
            novomc$shifted = true;
        }
    }

    @Inject(method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V", at = @At("RETURN"), remap = false)
    private void novomc$scrollEnd(List<String> lines, int x, int y, FontRenderer font, CallbackInfo ci) {
        if (novomc$shifted) {
            GlStateManager.popMatrix();
            novomc$shifted = false;
        }
    }
}
