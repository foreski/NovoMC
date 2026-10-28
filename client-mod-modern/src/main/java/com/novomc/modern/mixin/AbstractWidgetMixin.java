package com.novomc.modern.mixin;

import com.novomc.modern.ui.NovoUi;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Option widgets in Novo's settings rows show only their value; the row has the name. */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
    @Inject(method = "getMessage", at = @At("RETURN"), cancellable = true)
    private void novomc$valueOnly(CallbackInfoReturnable<Component> cir) {
        if (NovoUi.VALUE_ONLY.contains(this)) {
            String s = cir.getReturnValue().getString();
            int i = s.indexOf(": ");
            if (i > 0) {
                cir.setReturnValue(Component.literal(s.substring(i + 2)));
            }
        }
    }
}
