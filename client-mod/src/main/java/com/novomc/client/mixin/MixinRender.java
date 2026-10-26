package com.novomc.client.mixin;

import com.novomc.client.modules.NametagsModule;
import net.minecraft.client.renderer.entity.Render;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Render.class)
public abstract class MixinRender {
    /** Nametags: the label background's alpha (vanilla 0.25). */
    @ModifyConstant(method = "renderLivingLabel", constant = @Constant(floatValue = 0.25F))
    private float novomc$labelBackground(float alpha) {
        return NametagsModule.backgroundAlpha(alpha);
    }
}
