package com.novomc.client.mixin;

import com.novomc.client.modules.NametagsModule;
import com.novomc.client.modules.NovoBadgeModule;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public abstract class MixinRender {
    /** Room the NovoMC badge takes left of the name (badge + gap). */
    private static final int BADGE_ROOM = NovoBadgeModule.SIZE + 2;

    private boolean novomc$badge;

    /** Nametags: the label background's alpha (vanilla 0.25). */
    @ModifyConstant(method = "renderLivingLabel", constant = @Constant(floatValue = 0.25F))
    private float novomc$labelBackground(float alpha) {
        return NametagsModule.backgroundAlpha(alpha);
    }

    // NovoMC Badge: the label's background grows by the badge's width, the
    // name shifts right by half of it, and the badge goes in the gap on the left.

    @Inject(method = "renderLivingLabel", at = @At("HEAD"))
    private void novomc$badgeCheck(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        novomc$badge = NovoBadgeModule.labelHas(entity, str);
    }

    @Redirect(method = "renderLivingLabel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0))
    private int novomc$backgroundWidth(FontRenderer font, String text) {
        return font.getStringWidth(text) + (novomc$badge ? BADGE_ROOM : 0);
    }

    @Redirect(method = "renderLivingLabel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 1))
    private int novomc$textWidthBehind(FontRenderer font, String text) {
        return font.getStringWidth(text) - (novomc$badge ? BADGE_ROOM : 0);
    }

    @Redirect(method = "renderLivingLabel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 2))
    private int novomc$textWidth(FontRenderer font, String text) {
        return font.getStringWidth(text) - (novomc$badge ? BADGE_ROOM : 0);
    }

    /** The see-through pass (behind walls): a faint badge, like the faint name. */
    @Redirect(method = "renderLivingLabel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0))
    private int novomc$drawBehind(FontRenderer font, String text, int x, int y, int color) {
        int r = font.drawString(text, x, y, color);
        if (novomc$badge) {
            NovoBadgeModule.draw(x - BADGE_ROOM, y, 0.3F);
        }
        return r;
    }

    @Redirect(method = "renderLivingLabel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1))
    private int novomc$draw(FontRenderer font, String text, int x, int y, int color) {
        int r = font.drawString(text, x, y, color);
        if (novomc$badge) {
            NovoBadgeModule.draw(x - BADGE_ROOM, y, 1F);
        }
        return r;
    }
}
