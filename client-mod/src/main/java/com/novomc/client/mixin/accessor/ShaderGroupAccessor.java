package com.novomc.client.mixin.accessor;

import java.util.List;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The passes of a post shader, for setting uniforms every frame (Motion Blur). */
@Mixin(ShaderGroup.class)
public interface ShaderGroupAccessor {
    @Accessor("listShaders")
    List<Shader> novomc$getShaders();
}
