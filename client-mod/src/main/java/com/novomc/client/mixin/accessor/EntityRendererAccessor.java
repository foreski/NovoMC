package com.novomc.client.mixin.accessor;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Loads and swaps post-processing shaders (Motion Blur, Menu Blur). */
@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Invoker("loadShader")
    void novomc$loadShader(ResourceLocation shader);

    /** Puts an already-loaded shader group in place without recompiling it. */
    @Accessor("theShaderGroup")
    void novomc$setShaderGroup(ShaderGroup group);

    @Accessor("useShader")
    void novomc$setUseShader(boolean use);
}
