package com.novomc.modern.ui;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;

/** Novo's Options screen on modern Minecraft, in place of vanilla's (Custom Menus). */
public class NovoOptions extends NovoSettings {
    public NovoOptions(Screen parent) {
        super(parent, "Options");
    }

    @Override
    protected String[][] sections() {
        return new String[][] {{"general", "General"}, {"novo", "Novo Client"}};
    }

    @Override
    protected String subtitle() {
        return "Game settings and Novo Client";
    }

    @Override
    protected void rows(String section, List<Row> out) {
        var o = minecraft.options;
        if ("general".equals(section)) {
            out.add(option(o.fov(), "Field of view", null));
            out.add(link("Video Settings", "Render distance, framerate, graphics", () -> new NovoVideo(this)));
            out.add(link("Controls", "Key binds and mouse", () -> new ControlsScreen(this, o)));
            out.add(link("Music & Sounds", null, () -> new SoundOptionsScreen(this, o)));
            out.add(link("Skin Customization", "Skin layers and main hand", () -> new SkinCustomizationScreen(this, o)));
            out.add(link("Chat Settings", null, () -> new ChatOptionsScreen(this, o)));
            out.add(link("Language", null, () -> new LanguageSelectScreen(this, o, minecraft.getLanguageManager())));
            out.add(link("Accessibility", null, () -> new AccessibilityOptionsScreen(this, o)));
            out.add(link("More options", "Vanilla's options: resource packs, online, telemetry", () -> {
                NovoUi.vanillaNext = true;
                return new OptionsScreen(this, o);
            }));
        } else {
            out.add(link("Mods", "Switch mods on or off and change their settings", () -> new NovoMods(this)));
            out.add(link("Waypoints", "Your saved spots on this server or world", () -> new NovoWaypoints(this)));
            out.add(link("Trainers", "Crystal speed, mace dives and spear charges", () -> new NovoTrainers(this)));
            if (minecraft.level != null) {
                out.add(link("Edit HUD", "Move and resize HUD mods (Right Shift)", () -> new NovoHudEditor(this)));
            }
            out.add(module("custom_menus", "Custom menus", "Novo's look for every menu"));
            out.add(module("main_menu", "Main menu", "Novo's main menu"));
            out.add(module("loading_screen", "Loading screen", "Novo's loading screen"));
        }
    }
}
