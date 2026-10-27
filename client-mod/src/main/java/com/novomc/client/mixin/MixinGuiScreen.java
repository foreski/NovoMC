package com.novomc.client.mixin;

import com.novomc.client.gui.NovoTheme;
import com.novomc.client.modules.ScrollableTooltipsModule;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scrollable Tooltips: shifts the tooltip by the scroll offset. Targets
 * Forge's FontRenderer overload, which every item tooltip goes through
 * (Forge-added, so not remapped). Custom Menus: the dirt background
 * outside a world becomes NovoMC's dark backdrop.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {
    @Shadow public int width;
    @Shadow public int height;
    private boolean novomc$shifted;

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void novomc$background(int tint, CallbackInfo ci) {
        if (NovoTheme.enabled) {
            NovoTheme.background(width, height);
            ci.cancel();
        }
    }

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
