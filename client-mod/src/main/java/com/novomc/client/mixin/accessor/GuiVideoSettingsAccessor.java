package com.novomc.client.mixin.accessor;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The screen Video Settings returns to, so NovoMC's version can return there too. */
@Mixin(GuiVideoSettings.class)
public interface GuiVideoSettingsAccessor {
    @Accessor("parentGuiScreen")
    GuiScreen novomc$getParent();
}
