package com.novomc.client.mixin;

import com.novomc.client.gui.NovoTheme;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Custom Menus: vanilla buttons and sliders drawn in NovoMC's style. */
@Mixin(GuiButton.class)
public abstract class MixinGuiButton extends Gui {
    @Shadow public int xPosition;
    @Shadow public int yPosition;
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow protected boolean hovered;

    private float novomc$hover;
    private long novomc$lastFrame;

    @Redirect(method = "drawButton", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiButton;drawTexturedModalRect(IIIIII)V", ordinal = 0))
    private void novomc$background(GuiButton self, int x, int y, int u, int v, int w, int h) {
        if (NovoTheme.enabled) {
            boolean slider = self instanceof GuiOptionSlider || self instanceof GuiSlider;
            int state = (v - 46) / 20;
            // Sliders report state 0 even when hovered, so ease toward the hover flag itself.
            long now = System.nanoTime();
            float target = state == 2 || slider && hovered ? 1 : 0;
            novomc$hover += (target - novomc$hover) * (novomc$lastFrame == 0 ? 1 : NovoTheme.step(now - novomc$lastFrame));
            novomc$lastFrame = now;
            NovoTheme.button(xPosition, yPosition, width, height, state, slider, novomc$hover);
        } else {
            drawTexturedModalRect(x, y, u, v, w, h);
        }
    }

    @Redirect(method = "drawButton", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiButton;drawTexturedModalRect(IIIIII)V", ordinal = 1))
    private void novomc$backgroundRight(GuiButton self, int x, int y, int u, int v, int w, int h) {
        if (!NovoTheme.enabled) {
            drawTexturedModalRect(x, y, u, v, w, h);
        }
    }

    @Redirect(method = "drawButton", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiButton;drawCenteredString(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"))
    private void novomc$label(GuiButton self, FontRenderer font, String text, int x, int y, int color) {
        if (NovoTheme.enabled) {
            NovoTheme.label(text, x, y, color);
        } else {
            drawCenteredString(font, text, x, y, color);
        }
    }
}
