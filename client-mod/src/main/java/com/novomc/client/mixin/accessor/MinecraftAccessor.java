package com.novomc.client.mixin.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Package-private and private bits of Minecraft used by the loading screen and borderless fullscreen. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("running")
    boolean novomc$isRunning();

    /** Resizes the framebuffer and the open screen to the window. */
    @Invoker("resize")
    void novomc$resize(int width, int height);
}
