package com.novomc.client.modules;

import com.novomc.client.gui.GuiNovoMenu;
import com.novomc.client.module.Module;
import com.novomc.client.gui.GuiNovoPause;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Swaps the vanilla title screen for the NovoMC menu. */
public class MainMenuModule extends Module {
    public MainMenuModule() {
        super("main_menu");
    }

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        if (isEnabled() && event.gui != null && event.gui.getClass() == GuiMainMenu.class) {
            event.gui = new GuiNovoMenu();
        } else if (isEnabled() && event.gui != null && event.gui.getClass() == GuiIngameMenu.class) {
            event.gui = new GuiNovoPause();
        }
    }

    @Override
    protected void onEnable() {
        if (mc.currentScreen != null && mc.currentScreen.getClass() == GuiMainMenu.class) {
            mc.displayGuiScreen(new GuiNovoMenu());
        }
    }
}
