package com.novomc.modern.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.novomc.modern.OverlayTextureAccess;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Hit Color: the top half of the 16x16 overlay texture is the hurt tint. */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements OverlayTextureAccess {
    @Shadow @Final private DynamicTexture texture;

    @Override
    public void novomc$setHurtColor(int argb) {
        NativeImage pixels = texture.getPixels();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                pixels.setPixel(x, y, argb);
            }
        }
        texture.upload();
    }
}
