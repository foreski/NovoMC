package com.novomc.client.mixin;

import com.novomc.client.modules.GlintModule;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderItem.class)
public abstract class MixinRenderItem {
    /** Glint Colorizer: the item glint is drawn with this ARGB color. */
    @ModifyArg(
            method = "renderEffect",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderModel(Lnet/minecraft/client/resources/model/IBakedModel;I)V"),
            index = 1)
    private int novomc$glintColor(int color) {
        int c = GlintModule.color();
        return c != 0 ? c : color;
    }
}
