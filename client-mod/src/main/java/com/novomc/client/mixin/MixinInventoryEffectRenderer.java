package com.novomc.client.mixin;

import com.novomc.client.modules.PatcherFixesModule;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Patcher Fixes: keep the inventory centred when you have potion effects (they're drawn to its left). */
@Mixin(InventoryEffectRenderer.class)
public abstract class MixinInventoryEffectRenderer extends GuiContainer {
    @Shadow private boolean hasActivePotionEffects;

    private MixinInventoryEffectRenderer(Container container) {
        super(container);
    }

    @Inject(method = "updateActivePotionEffects", at = @At("HEAD"), cancellable = true)
    private void novomc$centred(CallbackInfo ci) {
        if (PatcherFixesModule.inventoryFix()) {
            guiLeft = (width - xSize) / 2;
            hasActivePotionEffects = !mc.thePlayer.getActivePotionEffects().isEmpty();
            ci.cancel();
        }
    }
}
