package com.novomc.client.mixin;

import com.novomc.client.modules.OldAnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 Visuals: 1.8 passes a swing progress of 0 while blocking, eating or
 * drawing a bow, so the arm never swings; this passes the real progress.
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {
    private float novomc$partialTicks;

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"))
    private void novomc$capturePartialTicks(float partialTicks, CallbackInfo ci) {
        novomc$partialTicks = partialTicks;
    }

    @ModifyArg(
            method = "renderItemInFirstPerson",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;transformFirstPersonItem(FF)V"),
            index = 1)
    private float novomc$swingWhileUsing(float swingProgress) {
        Minecraft mc = Minecraft.getMinecraft();
        return OldAnimationsModule.blockHit() && mc.thePlayer != null ? mc.thePlayer.getSwingProgress(novomc$partialTicks) : swingProgress;
    }
}
