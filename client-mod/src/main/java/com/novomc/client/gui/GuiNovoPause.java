package com.novomc.client.gui;

import com.novomc.client.util.Draw;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;

/**
 * The Esc menu in NovoMC's style. Vanilla's buttons (and their behavior:
 * disconnect, Open to LAN, Forge's mod options...) are kept and laid out as
 * compact NovoMC buttons, with Mods and Resource Packs added.
 */
public class GuiNovoPause extends GuiIngameMenu {
    private static final int RETURN = 4;
    private static final int OPTIONS = 0;
    private static final int DISCONNECT = 1;
    private static final int ACHIEVEMENTS = 5;
    private static final int STATS = 6;
    private static final int LAN = 7;
    private static final int MOD_OPTIONS = 12;
    private static final int MODS = 100;
    private static final int PACKS = 101;
    private static final int SCREENSHOTS = 102;
    private static final int WAYPOINTS = 103;
    private static final int ROWS = 7;
    private static final int W = 184;
    private static final int H = 18;
    private static final int GAP = 4;

    private int top;

    @Override
    public void initGui() {
        super.initGui();
        Map<Integer, GuiButton> vanilla = new HashMap<Integer, GuiButton>();
        for (GuiButton b : buttonList) {
            vanilla.put(b.id, b);
        }
        buttonList.clear();
        top = height / 2 - (ROWS * (H + GAP) - GAP) / 2 + 6;
        int left = width / 2 - W / 2;
        int half = (W - GAP) / 2;
        int y = top;
        add(vanilla, RETURN, left, y, W, "Back to Game");
        y += H + GAP;
        buttonList.add(new NovoButton(MODS, left, y, half, H, "Mods"));
        buttonList.add(new NovoButton(PACKS, left + half + GAP, y, half, H, "Resource Packs"));
        y += H + GAP;
        add(vanilla, OPTIONS, left, y, half, "Options");
        add(vanilla, MOD_OPTIONS, left + half + GAP, y, half, "Mod Options");
        y += H + GAP;
        add(vanilla, ACHIEVEMENTS, left, y, half, "Achievements");
        add(vanilla, STATS, left + half + GAP, y, half, "Statistics");
        y += H + GAP;
        buttonList.add(new NovoButton(SCREENSHOTS, left, y, half, H, "Screenshots"));
        buttonList.add(new NovoButton(WAYPOINTS, left + half + GAP, y, half, H, "Waypoints"));
        y += H + GAP;
        add(vanilla, LAN, left, y, W, "Open to LAN");
        y += H + GAP + 6;
        GuiButton quit = vanilla.get(DISCONNECT);
        add(vanilla, DISCONNECT, left, y, W, quit != null ? quit.displayString : "Disconnect");
    }

    /** Re-creates a vanilla button as a NovoButton with the same id and enabled state. */
    private void add(Map<Integer, GuiButton> vanilla, int id, int x, int y, int w, String label) {
        GuiButton original = vanilla.get(id);
        if (original == null) {
            return;
        }
        NovoButton b = new NovoButton(id, x, y, w, H, label);
        b.enabled = original.enabled;
        buttonList.add(b);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == MODS) {
            mc.displayGuiScreen(new GuiModules(this));
        } else if (button.id == PACKS) {
            mc.displayGuiScreen(new GuiResourcePacks(this));
        } else if (button.id == SCREENSHOTS) {
            mc.displayGuiScreen(new GuiScreenshots(this));
        } else if (button.id == WAYPOINTS) {
            mc.displayGuiScreen(new GuiWaypoints(this));
        } else {
            super.actionPerformed(button);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - W / 2 - 12;
        int right = width / 2 + W / 2 + 12;
        int bottom = top + ROWS * (H + GAP) + 6 + 10;
        Draw.round(left, top - 30, right, bottom, 8, 0xCC0C1019);
        Draw.roundOutline(left, top - 30, right, bottom, 8, 0x22FFFFFF);
        String title = "Game Menu";
        Draw.text(title, (width - Draw.width(title)) / 2f, top - 19, 0xFFFFFFFF);
        List<GuiButton> buttons = new ArrayList<GuiButton>(buttonList);
        for (GuiButton b : buttons) {
            b.drawButton(mc, mouseX, mouseY);
        }
    }
}
