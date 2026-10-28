package com.novomc.modern.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.novomc.modern.ui.NovoUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Custom Menus: text fields get Novo's rounded field with an accent outline when focused. */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {
    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void novomc$field(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        if (NovoUi.enabled) {
            NovoUi.field(g, x, y, w, h, ((AbstractWidget) (Object) this).isFocused());
        } else {
            g.blitSprite(pipeline, sprite, x, y, w, h);
        }
    }
}
