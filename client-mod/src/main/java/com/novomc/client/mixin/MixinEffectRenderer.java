package com.novomc.client.mixin;

import com.novomc.client.modules.ParticleLimiterModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops new particles once the Particle Limiter's cap is reached. */
@Mixin(EffectRenderer.class)
public abstract class MixinEffectRenderer {
    @Shadow
    private List<EntityFX>[][] fxLayers;

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void novomc$limitParticles(EntityFX effect, CallbackInfo ci) {
        int limit = ParticleLimiterModule.limit();
        if (limit < 0) {
            return;
        }
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view != null && effect.getDistanceSqToEntity(view) > ParticleLimiterModule.maxDistanceSq()) {
            ci.cancel();
            return;
        }
        int count = ParticleLimiterModule.trackedParticles();
        if (count < 0) {
            count = novomc$countParticles();
            ParticleLimiterModule.refreshParticleCount(count);
        }
        if (count >= limit) {
            ci.cancel();
        } else {
            ParticleLimiterModule.particleAdded();
        }
    }

    @Inject(method = "updateEffects", at = @At("RETURN"))
    private void novomc$refreshParticleCount(CallbackInfo ci) {
        if (ParticleLimiterModule.limit() < 0) {
            return;
        }
        ParticleLimiterModule.refreshParticleCount(novomc$countParticles());
    }

    private int novomc$countParticles() {
        int count = 0;
        for (List<EntityFX>[] layer : fxLayers) {
            for (List<EntityFX> list : layer) {
                count += list.size();
            }
        }
        return count;
    }
}
