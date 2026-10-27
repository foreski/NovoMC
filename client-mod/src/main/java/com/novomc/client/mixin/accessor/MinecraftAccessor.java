package com.novomc.client.mixin.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether the game is still running (package-private in vanilla); used by the loading screen. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("running")
    boolean novomc$isRunning();
}
