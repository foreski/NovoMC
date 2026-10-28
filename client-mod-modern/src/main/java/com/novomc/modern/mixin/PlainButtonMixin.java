package com.novomc.modern.mixin;

import com.novomc.modern.ui.NovoUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Menus: plain text buttons get Novo's background and their label in the Novo font. */
@Mixin(Button.Plain.class)
public abstract class PlainButtonMixin {
    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void novomc$contents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (NovoUi.enabled) {
            AbstractWidget w = (AbstractWidget) (Object) this;
            boolean hovered = w.isHoveredOrFocused();
            NovoUi.button(g, this, w.getX(), w.getY(), w.getWidth(), w.getHeight(), hovered, w.active);
            NovoUi.label(g, w.getMessage(), w.getX(), w.getY(), w.getWidth(), w.getHeight(), hovered, w.active);
            ci.cancel();
        }
    }
}
