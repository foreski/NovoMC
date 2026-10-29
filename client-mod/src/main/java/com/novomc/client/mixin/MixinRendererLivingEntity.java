package com.novomc.client.mixin;

import com.novomc.client.modules.HitColorModule;
import com.novomc.client.modules.NametagsModule;
import com.novomc.client.render.NovoCosmetics;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity {
    @Shadow
    protected ModelBase mainModel;

    private int novomc$puts;
    private boolean novomc$hurt;

    @Inject(method = "setBrightness", at = @At("HEAD"))
    private void novomc$startBrightness(EntityLivingBase entity, float partialTicks, boolean combineTextures, CallbackInfoReturnable<Boolean> cir) {
        novomc$puts = 0;
        novomc$hurt = entity.hurtTime > 0 || entity.deathTime > 0;
    }

    /** Hit Color: the first four floats written are the hurt tint (r, g, b, a). */
    @Redirect(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;"))
    private FloatBuffer novomc$hitColor(FloatBuffer buffer, float value) {
        int i = novomc$puts++;
        if (novomc$hurt && i < 4 && HitColorModule.active()) {
            value = HitColorModule.component(i);
        }
        return buffer.put(value);
    }

    /** Wings and hats: drawn after the player model, while its pose is still bound. */
    @Inject(method = "renderLayers", at = @At("HEAD"))
    private void novomc$cosmetics(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                  float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        if (entity instanceof EntityPlayer) {
            NovoCosmetics.render((EntityPlayer) entity, mainModel, scale);
        }
    }

    /** Nametags: your own nametag in third person. */
    @Inject(method = "canRenderName", at = @At("HEAD"), cancellable = true)
    private void novomc$ownNametag(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        if (NametagsModule.showOwn(entity)) {
            cir.setReturnValue(Minecraft.isGuiEnabled());
        }
    }
}
