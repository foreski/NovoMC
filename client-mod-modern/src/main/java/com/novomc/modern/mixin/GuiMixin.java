package com.novomc.modern.mixin;

import com.novomc.modern.ui.NovoOptions;
import com.novomc.modern.ui.NovoTitleScreen;
import com.novomc.modern.ui.NovoVideo;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import com.novomc.modern.ui.NovoUi;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Main Menu: vanilla's title screen becomes Novo's. Custom Menus: menus fade in when opened. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen novomc$screen(Screen screen) {
        if (screen != null && screen.getClass() == TitleScreen.class && NovoUi.mainMenu) {
            screen = new NovoTitleScreen();
        }
        if (screen != null && NovoUi.enabled && !NovoUi.vanillaNext) {
            if (screen.getClass() == OptionsScreen.class) {
                screen = new NovoOptions(((OptionsScreen) screen).getLastScreen());
            } else if (screen.getClass() == VideoSettingsScreen.class) {
                screen = new NovoVideo(((OptionsSubScreenAccessor) screen).novomc$lastScreen());
            }
        }
        NovoUi.vanillaNext = false;
        if (screen != null) {
            NovoUi.opened(screen);
        }
        return screen;
    }
}
