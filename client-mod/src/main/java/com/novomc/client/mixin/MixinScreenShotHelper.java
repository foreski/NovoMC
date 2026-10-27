package com.novomc.client.mixin;

import com.novomc.client.modules.ScreenshotsModule;
import java.io.File;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Screenshot manager: adds [Open] [Copy] [Delete] to the "Saved screenshot" chat message. */
@Mixin(ScreenShotHelper.class)
public abstract class MixinScreenShotHelper {
    @Inject(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
            at = @At("RETURN"), cancellable = true)
    private static void novomc$actions(File dir, String name, int w, int h, Framebuffer fb, CallbackInfoReturnable<IChatComponent> cir) {
        cir.setReturnValue(ScreenshotsModule.decorate(cir.getReturnValue()));
    }
}
