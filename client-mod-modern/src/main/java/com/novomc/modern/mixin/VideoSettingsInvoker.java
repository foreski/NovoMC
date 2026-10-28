package com.novomc.modern.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's Video Settings option lists, for Novo's Video Settings. */
@Mixin(VideoSettingsScreen.class)
public interface VideoSettingsInvoker {
    @Invoker("displayOptions")
    static OptionInstance<?>[] novomc$display(Options options) {
        throw new AssertionError();
    }

    @Invoker("qualityOptions")
    static OptionInstance<?>[] novomc$quality(Options options) {
        throw new AssertionError();
    }

    @Invoker("preferenceOptions")
    static OptionInstance<?>[] novomc$preferences(Options options) {
        throw new AssertionError();
    }
}
