package com.novomc.client.mixin;

import com.novomc.client.render.BlockTweaks;
import com.novomc.client.render.SmartAnimations;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunk building: Smart Animations bookkeeping, Better Grass/Snow and Connected Textures. */
@Mixin(BlockRendererDispatcher.class)
public abstract class MixinBlockRendererDispatcher {
    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void novomc$animated(IBlockState state, BlockPos pos, IBlockAccess world, WorldRenderer wr,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (SmartAnimations.enabled) {
            SmartAnimations.block(state);
        }
    }

    @Redirect(method = "renderBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BlockModelRenderer;renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;)Z"))
    private boolean novomc$tweaks(BlockModelRenderer renderer, IBlockAccess world, IBakedModel model, IBlockState state,
                                  BlockPos pos, WorldRenderer wr) {
        return BlockTweaks.renderModel(renderer, world, model, state, pos, wr);
    }
}
