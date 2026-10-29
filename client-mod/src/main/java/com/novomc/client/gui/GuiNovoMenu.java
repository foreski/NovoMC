package com.novomc.client.gui;

import com.novomc.client.NovoMC;
import com.novomc.client.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;
import org.lwjgl.opengl.GL11;

/**
 * NovoMC's main menu: the client's own night-sky backdrop instead of vanilla's
 * blurred panorama, the NovoMC logo and wordmark, glass buttons, and quick
 * icons at the bottom.
 *
 * Cosmetics are no longer previewed here: the in-game Cosmetics screen (the
 * shirt icon in the Novo menu) is the one place a cape, wings or hat is picked
 * and worn, so the title screen stays a menu.
 *
 * Extends GuiMainMenu so code checking for the title screen still works.
 */
public class GuiNovoMenu extends GuiMainMenu {
    private static final ResourceLocation LOGO = tex("logo");
    private static final ResourceLocation BACKGROUND = tex("menu_background");
    /** Pixel size of the backdrop, used to crop it to the window. */
    private static final float BG_W = 1600f;
    private static final float BG_H = 900f;
    private static final ResourceLocation USER = tex("icons/user");
    private static final ResourceLocation CLOSE = tex("icons/x");
    private static final ResourceLocation SETTINGS = tex("icons/settings");
    private static final ResourceLocation GLOBE = tex("icons/globe");
    private static final ResourceLocation PUZZLE = tex("icons/puzzle");
    private static final ResourceLocation GRID = tex("icons/layout-grid");
    private static final ResourceLocation SWORDS = tex("icons/swords");
    private static final ResourceLocation LAYERS = tex("icons/layers");
    private static final ResourceLocation TARGET = tex("icons/target");

    private static final int SINGLEPLAYER = 1;
    private static final int MULTIPLAYER = 2;
    private static final int ACCOUNT = 10;
    private static final int QUIT = 11;
    private static final int OPTIONS = 20;
    private static final int LANGUAGE = 21;
    private static final int FORGE_MODS = 22;
    private static final int NOVO_MODS = 23;
    private static final int PACKS = 24;
    private static final int CARD = 30;
    private static final int TRAINERS = 25;

    private final long openedAt = System.currentTimeMillis();

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(NovoMC.MODID, "textures/gui/" + name + ".png");
    }

    @Override
    public void initGui() {
        // Vanilla sets up the panorama textures here; its buttons are replaced below.
        super.initGui();
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;

        buttonList.add(new NovoButton(SINGLEPLAYER, cx - 80, cy + 4, 160, 18, "Singleplayer"));
        buttonList.add(new NovoButton(MULTIPLAYER, cx - 80, cy + 26, 160, 18, "Multiplayer"));

        buttonList.add(new NovoButton(ACCOUNT, 8, 8, 18, 18, "").icon(USER).tooltip("Skin layers"));
        buttonList.add(new NovoButton(QUIT, width - 26, 8, 18, 18, "").icon(CLOSE).tooltip("Quit game"));

        int[] ids = {OPTIONS, LANGUAGE, FORGE_MODS, NOVO_MODS, PACKS, TRAINERS};
        ResourceLocation[] icons = {SETTINGS, GLOBE, PUZZLE, GRID, LAYERS, TARGET};
        String[] tips = {"Options", "Language", "Forge mods", "Novo Client mods", "Resource packs", "Trainers"};
        int left = cx - (ids.length * 24 - 6) / 2;
        for (int i = 0; i < ids.length; i++) {
            buttonList.add(new NovoButton(ids[i], left + i * 24, height - 32, 18, 18, "").icon(icons[i]).tooltip(tips[i]));
        }

        buttonList.add(new MenuCard(CARD, width - 128, height - 66, 120, 44));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackdrop();
        GlStateManager.enableAlpha();
        // A light veil over the sky, heavier behind the buttons, so the labels
        // read on any monitor without hiding the artwork.
        drawGradientRect(0, 0, width, height, 0x38070A12, 0x6A070A12);
        drawGradientRect(0, height / 2, width, height, 0x00000000, 0x305B8CFF);

        float fade = Math.min(1f, (System.currentTimeMillis() - openedAt) / 500f);
        int alpha = Math.max(8, Math.round(fade * 255));
        int cx = width / 2;
        int cy = height / 2;

        Draw.icon(LOGO, cx - 26, cy - 98 + (1 - fade) * 6, 52, Draw.withAlpha(0xFFFFFF, alpha));
        String title = "NOVO CLIENT";
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy - 38, 0);
        GlStateManager.scale(1.6f, 1.6f, 1);
        Draw.spaced(title, -Draw.spacedWidth(title, 1.5f) / 2f, 0, Draw.withAlpha(0xFFFFFF, alpha), 1.5f);
        GlStateManager.popMatrix();
        String tagline = "PERFORMANCE PVP CLIENT";
        Draw.spaced(tagline, cx - Draw.spacedWidth(tagline, 1f) / 2f, cy - 18, Draw.withAlpha(0x8A94A6, alpha), 1f);

        Draw.text(mc.getSession().getUsername(), 32, 13, 0xFFD0D6E2);
        Draw.text("Novo Client " + NovoMC.VERSION + " (Minecraft 1.8.9)", 4, height - 10, 0xFF8A94A6);
        String legal = "Copyright Mojang AB. Do not distribute!";
        Draw.text(legal, width - Draw.width(legal) - 4, height - 10, 0xFF8A94A6);

        String tip = null;
        for (GuiButton b : buttonList) {
            b.drawButton(mc, mouseX, mouseY);
            if (b instanceof NovoButton && ((NovoButton) b).tooltip() != null) {
                tip = ((NovoButton) b).tooltip();
            }
        }
        if (tip != null) {
            int w = Draw.width(tip) + 8;
            int tx = Math.min(mouseX + 8, width - w - 2);
            int ty = mouseY - 16;
            Draw.rect(tx, ty, tx + w, ty + 13, 0xE0101624);
            Draw.outline(tx, ty, tx + w, ty + 13, NovoButton.ACCENT);
            Draw.text(tip, tx + 4, ty + 3, 0xFFFFFFFF);
        }
    }


    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case SINGLEPLAYER:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case MULTIPLAYER:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case ACCOUNT:
                mc.displayGuiScreen(new GuiCustomizeSkin(this));
                break;
            case QUIT:
                mc.shutdown();
                break;
            case OPTIONS:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case LANGUAGE:
                mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
                break;
            case FORGE_MODS:
                mc.displayGuiScreen(new GuiModList(this));
                break;
            case NOVO_MODS:
            case CARD:
                mc.displayGuiScreen(new GuiModules(this));
                break;
            case PACKS:
                mc.displayGuiScreen(new GuiResourcePacks(this));
                break;
            case TRAINERS:
                mc.displayGuiScreen(new GuiTrainers(this));
                break;
            default:
                break;
        }
    }

    /**
     * The title screen backdrop, cropped to the window (never squashed) and
     * drifting slowly inside the crop so the sky is not a frozen image.
     */
    private void drawBackdrop() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(BACKGROUND);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);

        float screenAspect = width / (float) Math.max(1, height);
        float textureAspect = BG_W / BG_H;
        float cropW = BG_W;
        float cropH = BG_H;
        if (screenAspect > textureAspect) {
            cropH = BG_W / screenAspect;
        } else {
            cropW = BG_H * screenAspect;
        }
        double seconds = (System.currentTimeMillis() - openedAt) / 1000.0;
        float u = (BG_W - cropW) / 2f + (float) Math.sin(seconds / 14.0) * (BG_W - cropW) * 0.35f;
        float v = (BG_H - cropH) / 2f + (float) Math.cos(seconds / 19.0) * (BG_H - cropH) * 0.30f;

        GlStateManager.pushMatrix();
        GlStateManager.scale(width / cropW, height / cropH, 1f);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, u, v, Math.round(cropW), Math.round(cropH), BG_W, BG_H);
        GlStateManager.popMatrix();
    }

    /** Bottom-right branded card that opens the NovoMC mods list. */
    private static final class MenuCard extends GuiButton {
        private float hover;

        MenuCard(int id, int x, int y, int w, int h) {
            super(id, x, y, w, h, "");
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            hover += ((hovered ? 1f : 0f) - hover) * 0.3f;
            int right = xPosition + width;
            int bottom = yPosition + height;
            drawGradientRect(xPosition, yPosition, right, bottom, Draw.blend(0xC03A2A78, 0xE04B3AA0, hover), Draw.blend(0xC01A1430, 0xE0281C50, hover));
            Draw.outline(xPosition, yPosition, right, bottom, Draw.blend(0x60B45BFF, 0xFFB45BFF, hover));
            Draw.icon(SWORDS, xPosition + 10, yPosition + (height - 20) / 2f, 20, 0xFFFFFFFF);
            Draw.spaced("NOVO", xPosition + 38, yPosition + 12, 0xFFFFFFFF, 1.5f);
            Draw.text("Mods & HUD", xPosition + 38, yPosition + 24, 0xFFC9B8FF);
        }
    }
}
