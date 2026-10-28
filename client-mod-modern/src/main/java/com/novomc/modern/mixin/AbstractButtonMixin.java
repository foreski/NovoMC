package com.novomc.modern.mixin;

import com.novomc.modern.ui.NovoUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Menus: every vanilla button background drawn in Novo's style. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void novomc$sprite(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (NovoUi.enabled) {
            AbstractWidget w = (AbstractWidget) (Object) this;
            NovoUi.button(g, this, w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.isHoveredOrFocused(), w.active);
            ci.cancel();
        }
    }
}
