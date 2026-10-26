package com.novomc.client.mixin;

import com.novomc.client.modules.NickHiderModule;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nick Hider: swaps your name in every string drawn (and measured, so layouts fit). */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {
    @ModifyVariable(method = "renderStringAtPos", at = @At("HEAD"), argsOnly = true)
    private String novomc$nickOnRender(String text) {
        return NickHiderModule.replace(text);
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true)
    private String novomc$nickOnWidth(String text) {
        return NickHiderModule.replace(text);
    }
}
