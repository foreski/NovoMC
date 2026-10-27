package com.novomc.client.mixin.accessor;

import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The screen Options returns to, so NovoMC's Options can return there too. */
@Mixin(GuiOptions.class)
public interface GuiOptionsAccessor {
    @Accessor("field_146441_g")
    GuiScreen novomc$getParent();
}
