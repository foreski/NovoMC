package com.novomc.client.mixin;

import com.novomc.client.render.LazyChunks;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Lazy Chunk Loading: the singleplayer server sends fewer chunks per tick. */
@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP {
    @ModifyConstant(method = "onUpdate", constant = @Constant(intValue = 10))
    private int novomc$chunksPerTick(int vanilla) {
        return LazyChunks.chunksPerTick();
    }
}
