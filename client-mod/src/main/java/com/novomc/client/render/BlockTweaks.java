package com.novomc.client.render;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockMycelium;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

/**
 * Per-block model changes made while chunks are built (on builder threads):
 * Better Grass, Better Snow and Connected Textures. Inspired by the OptiFine
 * options of the same names; NovoMC's own implementation.
 */
public final class BlockTweaks {
    public static final int GRASS_OFF = 0;
    public static final int GRASS_FAST = 1;
    public static final int GRASS_FANCY = 2;

    public static volatile int betterGrass;
    public static volatile boolean betterSnow;

    private static TextureAtlasSprite grassTop, grassSide, grassOverlay, snowedSide, snow, myceliumTop, myceliumSide;

    private BlockTweaks() {}

    /** Called after the block atlas is stitched. */
    static void spritesReady(TextureMap map) {
        grassTop = map.getAtlasSprite("minecraft:blocks/grass_top");
        grassSide = map.getAtlasSprite("minecraft:blocks/grass_side");
        grassOverlay = map.getAtlasSprite("minecraft:blocks/grass_side_overlay");
        snowedSide = map.getAtlasSprite("minecraft:blocks/grass_side_snowed");
        snow = map.getAtlasSprite("minecraft:blocks/snow");
        myceliumTop = map.getAtlasSprite("minecraft:blocks/mycelium_top");
        myceliumSide = map.getAtlasSprite("minecraft:blocks/mycelium_side");
    }

    /** Replaces {@code BlockModelRenderer.renderModel} for normal block models. */
    public static boolean renderModel(BlockModelRenderer renderer, IBlockAccess world, IBakedModel model, IBlockState state,
                                      BlockPos pos, WorldRenderer wr) {
        IBakedModel tweaked = model;
        if (betterGrass != GRASS_OFF && grassTop != null) {
            tweaked = betterGrass(tweaked, state, world, pos);
        }
        if (ConnectedTextures.active()) {
            tweaked = ConnectedTextures.apply(tweaked, state, world, pos);
        }
        boolean drawn = renderer.renderModel(world, tweaked, state, pos, wr);
        if (betterSnow && snowNextTo(state, world, pos)) {
            IBlockState layer = Blocks.snow_layer.getDefaultState();
            IBakedModel snowModel = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(layer);
            // Not the 5-argument overload: it would size the snow by the state at pos (the plant).
            drawn |= renderer.renderModel(world, snowModel, layer, pos, wr, true);
        }
        return drawn;
    }

    private static IBakedModel betterGrass(IBakedModel model, IBlockState state, IBlockAccess world, BlockPos pos) {
        Block block = state.getBlock();
        boolean grass = block == Blocks.grass;
        if (!grass && block != Blocks.mycelium) {
            return model;
        }
        boolean snowy = isSnowyTop(state);
        Quads.Model out = null;
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            if (betterGrass == GRASS_FANCY && !continues(block, snowy, world, pos.offset(side))) {
                continue;
            }
            List<BakedQuad> quads = model.getFaceQuads(side);
            if (quads.isEmpty()) {
                continue;
            }
            BakedQuad first = quads.get(0);
            TextureAtlasSprite from = Quads.spriteOf(first, grassSide, snowedSide, myceliumSide, grassOverlay);
            if (from == null) {
                continue;
            }
            if (out == null) {
                out = new Quads.Model(model);
            }
            List<BakedQuad> face = out.face(side);
            face.clear();
            if (snowy) {
                face.add(Quads.retexture(first, from, snow, -1));
            } else if (grass) {
                face.add(Quads.retexture(first, from, grassTop, 0));
            } else {
                face.add(Quads.retexture(first, from, myceliumTop, -1));
            }
        }
        return out != null ? out : model;
    }

    /** Fancy: a side turns green when the slope continues, i.e. the block diagonally below is the same top. */
    private static boolean continues(Block block, boolean snowy, IBlockAccess world, BlockPos side) {
        if (snowy) {
            Block next = world.getBlockState(side).getBlock();
            return next == Blocks.snow_layer || next == Blocks.snow || isSnowyTop(world.getBlockState(side.down()));
        }
        return world.getBlockState(side.down()).getBlock() == block;
    }

    private static boolean isSnowyTop(IBlockState state) {
        Block b = state.getBlock();
        return b == Blocks.grass ? state.getValue(BlockGrass.SNOWY) : b == Blocks.mycelium && state.getValue(BlockMycelium.SNOWY);
    }

    /** Better Snow: plants, fences and walls standing in snow get a snow layer at their base. */
    private static boolean snowNextTo(IBlockState state, IBlockAccess world, BlockPos pos) {
        Block b = state.getBlock();
        if (!(b instanceof BlockBush || b instanceof BlockFence || b instanceof BlockFenceGate || b instanceof BlockWall
                || b instanceof BlockPane) || b.getMaterial() == Material.water) {
            return false;
        }
        if (!world.getBlockState(pos.down()).getBlock().isFullCube()) {
            return false;
        }
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            Block next = world.getBlockState(pos.offset(side)).getBlock();
            if (next == Blocks.snow_layer || next == Blocks.snow) {
                return true;
            }
        }
        return false;
    }
}
