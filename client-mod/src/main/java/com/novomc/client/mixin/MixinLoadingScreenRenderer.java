package com.novomc.client.mixin;

import com.novomc.client.gui.NovoLoading;
import com.novomc.client.mixin.accessor.MinecraftAccessor;
import com.novomc.client.modules.LoadingScreenModule;
import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.MinecraftError;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loading Screen: singleplayer world loading ("Building terrain", "Preparing
 * spawn area") in NovoMC's style. Same timing and buffer handling as vanilla's
 * setLoadingProgress, with NovoMC's drawing in the middle.
 */
@Mixin(LoadingScreenRenderer.class)
public abstract class MixinLoadingScreenRenderer {
    @Shadow private String message;
    @Shadow private Minecraft mc;
    @Shadow private String currentlyDisplayedText;
    @Shadow private long systemTime;
    @Shadow private boolean loadingSuccess;
    @Shadow private Framebuffer framebuffer;

    @Inject(method = "setLoadingProgress", at = @At("HEAD"), cancellable = true)
    private void novomc$progress(int progress, CallbackInfo ci) {
        if (!LoadingScreenModule.on()) {
            return;
        }
        ci.cancel();
        if (!((MinecraftAccessor) mc).novomc$isRunning()) {
            if (!loadingSuccess) {
                throw new MinecraftError();
            }
            return;
        }
        long now = Minecraft.getSystemTime();
        // Vanilla redraws at most every 100 ms; a little faster keeps the spinner smooth.
        if (now - systemTime < 33) {
            return;
        }
        systemTime = now;
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();
        if (OpenGlHelper.isFramebufferEnabled()) {
            framebuffer.framebufferClear();
        } else {
            GlStateManager.clear(256);
        }
        framebuffer.bindFramebuffer(false);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0, 100, 300);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0, 0, -200);
        if (!OpenGlHelper.isFramebufferEnabled()) {
            GlStateManager.clear(16640);
        }
        NovoLoading.draw(w, h, currentlyDisplayedText, message, progress >= 0 ? progress / 100f : -1);
        framebuffer.unbindFramebuffer();
        if (OpenGlHelper.isFramebufferEnabled()) {
            framebuffer.framebufferRender(w * scale, h * scale);
        }
        mc.updateDisplay();
        try {
            Thread.yield();
        } catch (Exception ignored) {
            // same as vanilla
        }
    }
}
