package com.novomc.modern.mixin;

import com.novomc.modern.ui.NovoUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Menus: sliders as a Novo track and knob, label in the Novo font. */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
    @Shadow protected double value;

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void novomc$slider(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (NovoUi.enabled) {
            AbstractWidget w = (AbstractWidget) (Object) this;
            NovoUi.slider(g, this, w.getX(), w.getY(), w.getWidth(), w.getHeight(), value, w.getMessage(), w.isHoveredOrFocused(), w.active);
            ci.cancel();
        }
    }
}
