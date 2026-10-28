package com.novomc.client.mixin;

import com.novomc.client.render.CustomSky;
import com.novomc.client.modules.EntityCullingModule;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: resource-pack sky layers go over the sky colour, before the sun and moon. */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {
    @Shadow private WorldClient theWorld;

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;getRainStrength(F)F", ordinal = 0))
    private void novomc$customSky(float partialTicks, int pass, CallbackInfo ci) {
        CustomSky.render(theWorld, partialTicks);
    }

    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void novomc$beginEntityCullingFrame(net.minecraft.entity.Entity viewEntity,
            net.minecraft.client.renderer.culling.ICamera camera, float partialTicks,
            CallbackInfo ci) {
        EntityCullingModule.beginRenderFrame();
    }
}
