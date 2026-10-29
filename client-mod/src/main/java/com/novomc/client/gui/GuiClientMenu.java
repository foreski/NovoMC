package com.novomc.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.novomc.client.NovoClient;
import com.novomc.client.NovoMC;
import com.novomc.client.util.Draw;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/**
 * The RightShift menu: one screen of big tiles in front of the game, the way
 * Lunar's overlay works. Mods and Cosmetics sit side by side at the top — the
 * shirt icon is Cosmetics, next to the Mods tile, as on the reference — with
 * the HUD editor, waypoints, settings and resource packs under them.
 *
 * Esc or RightShift again closes it, and every tile hands off to the panel that
 * already does that job.
 */
public class GuiClientMenu extends GuiScreen {
    private static final int TILE_W = 132;
    private static final int TILE_H = 96;
    private static final int GAP = 10;
    private static final int COLUMNS = 3;

    private static final ResourceLocation SHIRT = icon("shirt");
    private static final ResourceLocation GRID = icon("layout-grid");
    private static final ResourceLocation MOVE = icon("move");
    private static final ResourceLocation TARGET = icon("target");
    private static final ResourceLocation SETTINGS = icon("settings");
    private static final ResourceLocation LAYERS = icon("layers");
    private static final ResourceLocation LOGO = new ResourceLocation(NovoMC.MODID, "textures/gui/logo.png");

    /** One tile: what it says, what it opens, and its icon. */
    private static final class Tile {
        final String label;
        final String hint;
        final ResourceLocation icon;
        final Runnable open;
        float hover;
        boolean wasHovered;

        Tile(String label, String hint, ResourceLocation icon, Runnable open) {
            this.label = label;
            this.hint = hint;
            this.icon = icon;
            this.open = open;
        }
    }

    private final List<Tile> tiles = new ArrayList<Tile>();
    private final boolean fromWorld;
    private long openedAt = System.currentTimeMillis();

    public GuiClientMenu() {
        this.fromWorld = mc != null && mc.theWorld != null;
        // Panels open with this menu as their parent, so Esc goes back here.
        tiles.add(new Tile("Mods", "Every mod and its settings", GRID, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiModules(GuiClientMenu.this));
            }
        }));
        tiles.add(new Tile("Cosmetics", "Cape, wings and hat", SHIRT, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiEditAvatar(GuiClientMenu.this));
            }
        }));
        tiles.add(new Tile("HUD Editor", "Drag the HUD where you want it", MOVE, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiHudEditor());
            }
        }));
        tiles.add(new Tile("Waypoints", "Saved places in this world", TARGET, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiWaypoints(GuiClientMenu.this));
            }
        }));
        tiles.add(new Tile("Settings", "Novo Client options", SETTINGS, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiNovoOptions(GuiClientMenu.this));
            }
        }));
        tiles.add(new Tile("Resource Packs", "Textures and packs", LAYERS, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiResourcePacks(GuiClientMenu.this));
            }
        }));
    }

    private static ResourceLocation icon(String name) {
        return new ResourceLocation("novomc", "textures/gui/icons/" + name + ".png");
    }

    private int gridLeft() {
        int width = COLUMNS * TILE_W + (COLUMNS - 1) * GAP;
        return (this.width - width) / 2;
    }

    private int gridTop() {
        int rows = (tiles.size() + COLUMNS - 1) / COLUMNS;
        int height = rows * TILE_H + (rows - 1) * GAP;
        return (this.height - height) / 2 + 14;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // The game stays visible behind the menu, just pushed back.
        if (fromWorld) {
            Draw.rect(0, 0, width, height, 0xC4080B12);
        } else {
            drawDefaultBackground();
        }
        float fade = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        int alpha = Math.max(8, Math.round(fade * 255));

        int top = gridTop();
        int cx = width / 2;
        // Wordmark over the tiles.
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, top - 52, 0);
        GlStateManager.scale(1.5f, 1.5f, 1);
        String title = "NOVO CLIENT";
        Draw.spaced(title, -Draw.spacedWidth(title, 1.6f) / 2f, 0, Draw.withAlpha(0xFFFFFF, alpha), 1.6f);
        GlStateManager.popMatrix();
        String hint = "RightShift or Esc closes this";
        Draw.text(hint, cx - Draw.width(hint) / 2f, top - 30, Draw.withAlpha(0x8A93A6, alpha));

        int left = gridLeft();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + col * (TILE_W + GAP);
            int y = top + row * (TILE_H + GAP);
            drawTile(tile, x, y, mouseX, mouseY, alpha);
        }

        String version = "Novo Client " + NovoMC.VERSION;
        Draw.text(version, 4, height - 10, 0xFF8A93A6);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTile(Tile tile, int x, int y, int mouseX, int mouseY, int alpha) {
        boolean hover = mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H;
        tile.hover += ((hover ? 1f : 0f) - tile.hover) * 0.25f;
        int right = x + TILE_W;
        int bottom = y + TILE_H;
        int lift = Math.round(tile.hover * 2f);
        Draw.round(x, y - lift, right, bottom - lift, 8, Draw.withAlpha(0xFF121A2A, Math.round(alpha * 0.92f)));
        Draw.roundOutline(x, y - lift, right, bottom - lift, 8,
                Draw.blend(0x30FFFFFF, 0xB45B8CFF, tile.hover));
        if (tile.hover > 0.02f) {
            Draw.round(x, y - lift, right, y - lift + 2, 2,
                    Draw.withAlpha(NovoButton.ACCENT, Math.round(tile.hover * 220)));
        }
        Draw.icon(tile.icon, x + (TILE_W - 24) / 2f, y - lift + 14, 24, 0xFFDCE4F2);
        String label = tile.label;
        Draw.bold(label, x + (TILE_W - Draw.width(label)) / 2f, y - lift + 48, 0xFFF2F5FA);
        String hint = Draw.trim(tile.hint, TILE_W - 16);
        Draw.text(hint, x + (TILE_W - Draw.width(hint)) / 2f, y - lift + 64, 0xFF8A93A6);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) {
            return;
        }
        int left = gridLeft();
        int top = gridTop();
        for (int i = 0; i < tiles.size(); i++) {
            int x = left + (i % COLUMNS) * (TILE_W + GAP);
            int y = top + (i / COLUMNS) * (TILE_H + GAP);
            if (mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H) {
                tileClicked(tiles.get(i));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private void tileClicked(Tile tile) {
        tile.open.run();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == NovoClient.MENU_KEY.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
