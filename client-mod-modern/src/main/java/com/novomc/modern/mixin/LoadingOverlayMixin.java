package com.novomc.modern.mixin;

import com.novomc.modern.ui.NovoUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loading Screen: Novo's loading screen over Mojang's (dark backdrop, logo,
 * wordmark and a blue progress bar). Vanilla still runs the reload and its
 * fade timing; this draws on top at the same opacity.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;
    @Shadow @Final private boolean fadeIn;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void novomc$loading(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!NovoUi.loading) {
            return;
        }
        long now = Util.getMillis();
        float out = fadeOutStart > -1 ? (now - fadeOutStart) / 1000f : -1;
        float in = fadeInStart > -1 ? (now - fadeInStart) / 500f : -1;
        float alpha = out >= 1 ? 1 - Math.min(1, out - 1) : fadeIn ? Math.max(0, Math.min(1, in)) : 1;
        if (alpha <= 0.01f) {
            return;
        }
        int a = Math.round(alpha * 255);
        int w = g.guiWidth();
        int h = g.guiHeight();
        g.nextStratum();
        g.fill(0, 0, w, h, NovoUi.withAlpha(0xFF070A12, a));
        g.fillGradient(0, h / 2, w, h, 0x00000000, NovoUi.withAlpha(0x5B8CFF, Math.round(alpha * 0x30)));
        int cx = w / 2;
        int cy = h / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, NovoUi.tex("logo"), cx - 24, cy - 60, 0, 0, 48, 48, 48, 48, NovoUi.withAlpha(0xFFFFFF, a));
        NovoUi.centered(g, NovoUi.bold("N O V O   C L I E N T"), cx, cy, NovoUi.withAlpha(0xFFFFFF, a));
        int bw = Math.min(220, w - 40);
        int bx = cx - bw / 2;
        int by = cy + 22;
        float p = Math.max(0, Math.min(1, currentProgress));
        g.fill(bx, by, bx + bw, by + 4, NovoUi.withAlpha(0xFF1A2130, a));
        g.fillGradient(bx, by, bx + Math.round(bw * p), by + 4, NovoUi.withAlpha(NovoUi.ACCENT, a), NovoUi.withAlpha(NovoUi.PURPLE, a));
        NovoUi.centered(g, NovoUi.text(Math.round(p * 100) + "%"), cx, by + 10, NovoUi.withAlpha(NovoUi.MUTED, a));
    }
}
