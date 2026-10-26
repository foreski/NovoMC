package com.novomc.client.mixin.accessor;

import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the NovoMC menu draw vanilla's blurred panorama. */
@Mixin(GuiMainMenu.class)
public interface GuiMainMenuAccessor {
    @Invoker("renderSkybox")
    void novomc$renderSkybox(int mouseX, int mouseY, float partialTicks);
}
