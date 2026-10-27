package com.novomc.client.mixin;

import com.novomc.client.gui.NovoTheme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Custom Menus: slider knobs drawn as a NovoMC track and knob. */
@Mixin(GuiOptionSlider.class)
public abstract class MixinGuiOptionSlider extends GuiButton {
    private MixinGuiOptionSlider() {
        super(0, 0, 0, "");
    }

    @Redirect(method = "mouseDragged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiOptionSlider;drawTexturedModalRect(IIIIII)V", ordinal = 0))
    private void novomc$knob(GuiOptionSlider self, int x, int y, int u, int v, int w, int h) {
        if (NovoTheme.enabled) {
            NovoTheme.sliderKnob(xPosition, yPosition, width, height, x);
        } else {
            drawTexturedModalRect(x, y, u, v, w, h);
        }
    }

    @Redirect(method = "mouseDragged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiOptionSlider;drawTexturedModalRect(IIIIII)V", ordinal = 1))
    private void novomc$knobRight(GuiOptionSlider self, int x, int y, int u, int v, int w, int h) {
        if (!NovoTheme.enabled) {
            drawTexturedModalRect(x, y, u, v, w, h);
        }
    }
}
