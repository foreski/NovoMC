package com.novomc.modern.mixin;

import com.novomc.modern.More;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.OptionsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Nametags: the background behind nametags. */
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @Redirect(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F"))
    private float novomc$nametagBackground(OptionsRenderState options, float fallback) {
        float custom = More.Nametags.background();
        return custom >= 0 ? custom : options.getBackgroundOpacity(fallback);
    }
}
