package com.novomc.modern.mixin;

import com.novomc.modern.More;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitbox, Chunk Borders and Waypoints draw their world overlays alongside vanilla's debug gizmos. */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void novomc$gizmos(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
        More.emitGizmos(partialTick);
    }
}
