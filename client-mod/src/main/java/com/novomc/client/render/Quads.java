package com.novomc.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;

/** Baked-quad helpers shared by Better Grass and Connected Textures. */
public final class Quads {
    private Quads() {}

    /** The sprite out of {@code candidates} whose atlas area holds the quad's UV centre, or null. */
    public static TextureAtlasSprite spriteOf(BakedQuad quad, TextureAtlasSprite... candidates) {
        int[] data = quad.getVertexData();
        int stride = data.length / 4;
        float u = 0;
        float v = 0;
        for (int i = 0; i < 4; i++) {
            u += Float.intBitsToFloat(data[i * stride + 4]);
            v += Float.intBitsToFloat(data[i * stride + 5]);
        }
        u /= 4;
        v /= 4;
        for (TextureAtlasSprite s : candidates) {
            if (s != null && u >= s.getMinU() && u <= s.getMaxU() && v >= s.getMinV() && v <= s.getMaxV()) {
                return s;
            }
        }
        return null;
    }

    /** A copy of {@code quad} with its UVs moved from {@code from} to the same spot in {@code to}. */
    public static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite from, TextureAtlasSprite to, int tintIndex) {
        int[] data = quad.getVertexData().clone();
        int stride = data.length / 4;
        float du = from.getMaxU() - from.getMinU();
        float dv = from.getMaxV() - from.getMinV();
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            float u = (Float.intBitsToFloat(data[o + 4]) - from.getMinU()) / du;
            float v = (Float.intBitsToFloat(data[o + 5]) - from.getMinV()) / dv;
            data[o + 4] = Float.floatToRawIntBits(to.getMinU() + u * (to.getMaxU() - to.getMinU()));
            data[o + 5] = Float.floatToRawIntBits(to.getMinV() + v * (to.getMaxV() - to.getMinV()));
        }
        return new BakedQuad(data, tintIndex, quad.getFace());
    }

    /** A model with its own quad lists and everything else from {@code base}. */
    public static final class Model implements IBakedModel {
        private final IBakedModel base;
        @SuppressWarnings("unchecked")
        final List<BakedQuad>[] faces = new List[6];
        List<BakedQuad> general;

        public Model(IBakedModel base) {
            this.base = base;
            for (EnumFacing f : EnumFacing.VALUES) {
                faces[f.ordinal()] = new ArrayList<BakedQuad>(base.getFaceQuads(f));
            }
            general = new ArrayList<BakedQuad>(base.getGeneralQuads());
        }

        public List<BakedQuad> face(EnumFacing f) {
            return faces[f.ordinal()];
        }

        public List<BakedQuad> general() {
            return general;
        }

        @Override
        public List<BakedQuad> getFaceQuads(EnumFacing facing) {
            return faces[facing.ordinal()];
        }

        @Override
        public List<BakedQuad> getGeneralQuads() {
            return general;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return base.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return base.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return base.isBuiltInRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return base.getParticleTexture();
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return base.getItemCameraTransforms();
        }
    }
}
