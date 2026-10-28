package com.novomc.modern;

import com.novomc.modern.ui.NovoUi;

/** The menu mods (Custom Menus, Main Menu, Loading Screen): switches for NovoUi. */
public final class Menus {
    private Menus() {}

    public static final class CustomMenus extends Module {
        public CustomMenus() {
            super("custom_menus");
        }

        @Override
        protected void onEnable() {
            NovoUi.enabled = true;
        }

        @Override
        protected void onDisable() {
            NovoUi.enabled = false;
        }
    }

    public static final class MainMenu extends Module {
        public MainMenu() {
            super("main_menu");
        }

        @Override
        protected void onEnable() {
            NovoUi.mainMenu = true;
        }

        @Override
        protected void onDisable() {
            NovoUi.mainMenu = false;
        }
    }

    public static final class LoadingScreen extends Module {
        public LoadingScreen() {
            super("loading_screen");
        }

        @Override
        protected void onEnable() {
            NovoUi.loading = true;
        }

        @Override
        protected void onDisable() {
            NovoUi.loading = false;
        }
    }
}
