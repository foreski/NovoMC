package com.novomc.client.modules;

import com.novomc.client.gui.GuiNovoOptions;
import com.novomc.client.gui.NovoTheme;
import com.novomc.client.mixin.accessor.GuiOptionsAccessor;
import com.novomc.client.module.Module;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * No vanilla-looking menus: Options becomes NovoMC's screen, and every other
 * vanilla screen (server list, worlds, controls, language, dialogs) is drawn
 * with NovoMC's buttons, sliders, fields, lists and backdrop.
 */
public class CustomMenusModule extends Module {
    public CustomMenusModule() {
        super("custom_menus");
    }

    @Override
    protected void onEnable() {
        NovoTheme.enabled = true;
    }

    @Override
    protected void onDisable() {
        NovoTheme.enabled = false;
    }

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        if (isEnabled() && event.gui != null && event.gui.getClass() == GuiOptions.class) {
            event.gui = new GuiNovoOptions(((GuiOptionsAccessor) event.gui).novomc$getParent());
        }
        if (isEnabled() && animated(event.gui)) {
            NovoTheme.opened();
        }
    }

    /** Menus fade in when opened; screens used mid-game (inventories, chat) open instantly. */
    @SubscribeEvent
    public void onDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (isEnabled() && animated(event.gui)) {
            NovoTheme.fadeIn(event.gui.width, event.gui.height);
        }
    }

    private static boolean animated(GuiScreen gui) {
        return gui != null && !(gui instanceof GuiContainer) && !(gui instanceof GuiChat);
    }
}
