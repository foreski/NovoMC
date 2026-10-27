package com.novomc.client.mixin;

import com.novomc.client.gui.NovoTheme;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Custom Menus: text fields get NovoMC's rounded field with an accent outline when focused. */
@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField extends Gui {
    @Shadow private boolean isFocused;

    @Redirect(method = "drawTextBox", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V", ordinal = 0))
    private void novomc$border(int l, int t, int r, int b, int color) {
        if (NovoTheme.enabled) {
            NovoTheme.fieldBorder(l, t, r, b, isFocused);
        } else {
            drawRect(l, t, r, b, color);
        }
    }

    @Redirect(method = "drawTextBox", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V", ordinal = 1))
    private void novomc$fill(int l, int t, int r, int b, int color) {
        if (!NovoTheme.enabled) {
            drawRect(l, t, r, b, color);
        }
    }
}
