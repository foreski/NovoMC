package com.novomc.client.mixin.accessor;

import net.minecraft.client.renderer.ChunkRenderContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The camera position chunks are drawn relative to (Sodium renderer). */
@Mixin(ChunkRenderContainer.class)
public interface ChunkRenderContainerAccessor {
    @Accessor("viewEntityX")
    double novomc$getViewX();

    @Accessor("viewEntityY")
    double novomc$getViewY();

    @Accessor("viewEntityZ")
    double novomc$getViewZ();
}
