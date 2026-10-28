package com.novomc.modern.ui;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Novo Client's main menu on modern Minecraft, the same layout as 1.8.9: the
 * panorama under a dark overlay, the logo and NOVO CLIENT wordmark,
 * Singleplayer / Multiplayer, an icon row and the account in the corner.
 */
public class NovoTitleScreen extends Screen {
    private final List<IconButton> icons = new ArrayList<IconButton>();
    private long openedAt;

    private record IconButton(Button button, String icon, String tip) {}

    public NovoTitleScreen() {
        super(Component.literal("Novo Client"));
    }

    @Override
    protected void init() {
        openedAt = Util.getMillis();
        icons.clear();
        int cx = width / 2;
        int cy = height / 2;
        addRenderableWidget(Button.builder(NovoUi.text("Singleplayer"), b -> minecraft.gui.setScreen(new SelectWorldScreen(this)))
                .bounds(cx - 80, cy + 4, 160, 18).build());
        addRenderableWidget(Button.builder(NovoUi.text("Multiplayer"), b -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this)))
                .bounds(cx - 80, cy + 26, 160, 18).build());

        icon(8, 8, "user", "Skin layers", () -> minecraft.gui.setScreen(new SkinCustomizationScreen(this, minecraft.options)));
        icon(width - 26, 8, "x", "Quit game", minecraft::stop);
        int left = cx - (4 * 24 - 6) / 2;
        icon(left, height - 32, "layout-grid", "Mods", () -> minecraft.gui.setScreen(new NovoMods(this)));
        icon(left + 24, height - 32, "trophy", "Trainers", () -> minecraft.gui.setScreen(new NovoTrainers(this)));
        icon(left + 48, height - 32, "settings", "Options", () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options)));
        icon(left + 72, height - 32, "globe", "Language",
                () -> minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())));
    }

    private void icon(int x, int y, String icon, String tip, Runnable action) {
        Button b = addRenderableWidget(Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, 18, 18).build());
        icons.add(new IconButton(b, icon, tip));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractPanorama(g, partialTick);
        g.fillGradient(0, 0, width, height, 0xA0070A12, 0xD0070A12);
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0x305B8CFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        float fade = Math.min(1f, (Util.getMillis() - openedAt) / 500f);
        int alpha = Math.max(8, Math.round(fade * 255));
        int cx = width / 2;
        int cy = height / 2;

        int lift = Math.round((1 - fade) * 6);
        g.blit(RenderPipelines.GUI_TEXTURED, NovoUi.tex("logo"), cx - 26, cy - 98 + lift, 0, 0, 52, 52, 52, 52, NovoUi.withAlpha(0xFFFFFF, alpha));
        Component title = NovoUi.bold("N O V O   C L I E N T");
        g.pose().pushMatrix();
        g.pose().translate(cx, cy - 40);
        g.pose().scale(1.5f, 1.5f);
        NovoUi.centered(g, title, 0, 0, NovoUi.withAlpha(0xFFFFFF, alpha));
        g.pose().popMatrix();
        NovoUi.centered(g, NovoUi.text("P E R F O R M A N C E   P V P   C L I E N T"), cx, cy - 18, NovoUi.withAlpha(0x8A94A6, alpha));

        NovoUi.draw(g, NovoUi.text(minecraft.getUser().getName()), 32, 13, 0xFFD0D6E2);
        String mc = FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        NovoUi.draw(g, NovoUi.text("Novo Client (Minecraft " + mc + ")"), 4, height - 11, 0xFF8A94A6);
        Component legal = NovoUi.text("Not an official Minecraft product");
        NovoUi.draw(g, legal, width - NovoUi.width(legal) - 4, height - 11, 0xFF8A94A6);

        String tip = null;
        for (IconButton ib : icons) {
            Button b = ib.button();
            boolean hover = b.isHovered();
            NovoUi.icon(g, ib.icon(), b.getX() + 4, b.getY() + 4, 10, hover ? 0xFFFFFFFF : 0xFFC9D1E0);
            if (hover) {
                tip = ib.tip();
            }
        }
        if (tip != null) {
            Component c = NovoUi.text(tip);
            int w = NovoUi.width(c) + 8;
            int tx = Math.min(mouseX + 8, width - w - 2);
            int ty = mouseY - 16;
            g.fill(tx, ty, tx + w, ty + 13, 0xE0101624);
            g.outline(tx, ty, w, 13, NovoUi.ACCENT);
            NovoUi.draw(g, c, tx + 4, ty + 3, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
