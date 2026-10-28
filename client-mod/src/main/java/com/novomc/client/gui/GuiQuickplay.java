package com.novomc.client.gui;

import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

/**
 * Quickplay: Hypixel games as cards; click one to see its modes, click a mode
 * to join it (/play), or open the game's lobby.
 */
public class GuiQuickplay extends NovoPanelScreen {
    private static final ResourceLocation PLAY = icon("swords");
    private static final ResourceLocation LOBBY = icon("globe");

    /** {name, lobby, then mode label/command pairs}. */
    static final String[][] GAMES = {
        {"Bed Wars", "bedwars", "Solo", "bedwars_eight_one", "Doubles", "bedwars_eight_two", "3v3v3v3", "bedwars_four_three",
            "4v4v4v4", "bedwars_four_four", "4v4", "bedwars_two_four"},
        {"SkyWars", "skywars", "Solo Normal", "solo_normal", "Solo Insane", "solo_insane", "Teams Normal", "teams_normal",
            "Teams Insane", "teams_insane"},
        {"Duels", "duels", "Classic", "duels_classic_duel", "Sumo", "duels_sumo_duel", "Bridge", "duels_bridge_duel",
            "UHC", "duels_uhc_duel", "OP", "duels_op_duel", "Combo", "duels_combo_duel", "Bow", "duels_bow_duel",
            "NoDebuff", "duels_potion_duel", "Boxing", "duels_boxing_duel", "SkyWars", "duels_sw_duel",
            "Blitz", "duels_blitz_duel", "Mega Walls", "duels_mw_duel", "Bow Spleef", "duels_bowspleef_duel"},
        {"The Pit", "pit", "Join", "pit"},
        {"Murder Mystery", "murder", "Classic", "murder_classic", "Double Up", "murder_double_up", "Assassins", "murder_assassins",
            "Infection", "murder_infection"},
        {"Build Battle", "build", "Solo", "build_battle_solo_normal", "Teams", "build_battle_teams_normal",
            "Guess the Build", "build_battle_guess_the_build"},
        {"TNT Games", "tnt", "TNT Run", "tnt_tntrun", "PVP Run", "tnt_pvprun", "Bow Spleef", "tnt_bowspleef", "TNT Tag", "tnt_tntag",
            "Wizards", "tnt_capture"},
        {"Mega Walls", "megawalls", "Standard", "mw_standard", "Face Off", "mw_face_off"},
        {"Blitz SG", "blitz", "Solo", "blitz_solo_normal", "Teams", "blitz_teams_normal"},
        {"UHC", "uhc", "Solo", "uhc_solo", "Teams", "uhc_teams"},
        {"Speed UHC", "uhc", "Solo", "speed_solo_normal", "Teams", "speed_team_normal"},
        {"Cops and Crims", "mcgo", "Defusal", "mcgo_normal", "Deathmatch", "mcgo_deathmatch"},
        {"Wool Wars", "woolgames", "4v4", "wool_wool_wars_two_four"},
        {"Arcade", "arcade", "Party Games", "arcade_party_games_1", "Hole in the Wall", "arcade_hole_in_the_wall",
            "Mini Walls", "arcade_mini_walls", "Football", "arcade_football", "Zombies", "arcade_zombies_dead_end",
            "Prop Hunt", "arcade_hide_and_seek_prop_hunt"},
        {"SkyBlock", "skyblock", "Join", "sb"},
    };
    private static final int[] COLORS = {0xFFF87171, 0xFF22D3EE, 0xFFFACC15, 0xFFFB923C, 0xFFB45BFF, 0xFF4ADE80, 0xFFF87171,
        0xFF5B8CFF, 0xFFFACC15, 0xFFFB923C, 0xFF22D3EE, 0xFF8A93A6, 0xFFE8ECF4, 0xFF4ADE80, 0xFF5B8CFF};
    private static final int CARD_H = 34;

    private int open = -1;

    public GuiQuickplay(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected String tab() {
        return "quickplay";
    }

    @Override
    protected void searchChanged() {
        super.searchChanged();
        open = -1;
    }

    /** Sends the command and closes the menu. */
    void run(String command) {
        mc.displayGuiScreen(null);
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage(command);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int[] p = beginFrame(mouseX, mouseY);
        int l = p[0], top = p[1], r = p[2];
        String ip = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP.toLowerCase() : "";
        drawHeader(l, r, top, "Quickplay", ip.contains("hypixel") ? "Join any Hypixel game in one click."
                : "Join Hypixel games in one click (you're not on Hypixel right now).");
        drawSearch(r - searchWidth(l, r), top + 31, r, "Search games");

        String q = search.toString().trim().toLowerCase();
        List<Integer> shown = new ArrayList<Integer>();
        for (int i = 0; i < GAMES.length; i++) {
            if (q.isEmpty() || GAMES[i][0].toLowerCase().contains(q)) {
                shown.add(i);
            }
        }
        int gridTop = top + 60;
        int bottom = p[3] - PAD;
        int cols = r - l >= 360 ? 3 : 2;
        float cw = (r - l - (cols - 1) * 6) / (float) cols;

        // Lay out: cards in rows; an open game's modes span the full width under its row.
        float content = 0;
        int rows = (shown.size() + cols - 1) / cols;
        for (int row = 0; row < rows; row++) {
            content += CARD_H + 6;
            if (openRow(shown, cols) == row) {
                content += modesHeight(open, l, r) + 6;
            }
        }
        float max = updateScroll(content, bottom - gridTop);
        clip(l - 1, gridTop, r + 1, bottom);
        float y = gridTop - scroll;
        for (int row = 0; row < rows; row++) {
            for (int c = 0; c < cols; c++) {
                int idx = row * cols + c;
                if (idx < shown.size()) {
                    card(shown.get(idx), l + c * (cw + 6), y, cw);
                }
            }
            y += CARD_H + 6;
            if (openRow(shown, cols) == row) {
                y = modes(open, l, y, r) + 6;
            }
        }
        noClip();
        if (shown.isEmpty()) {
            String hint = "No games match.";
            Draw.text(hint, (l + r - Draw.width(hint)) / 2f, gridTop + 20, MUTED);
        }
        drawScrollbar(r, gridTop, bottom, max);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int openRow(List<Integer> shown, int cols) {
        int i = shown.indexOf(open);
        return i < 0 ? -1 : i / cols;
    }

    /** Height of {@link #modes} for this game, laid out the same way. */
    private float modesHeight(int game, float l, float r) {
        String[] g = GAMES[game];
        float x = l + Draw.width("Lobby") + 30;
        float h = 16;
        for (int i = 2; i + 1 < g.length; i += 2) {
            float w = Draw.width(g[i]) + 16;
            if (x + w > r) {
                x = l;
                h += 20;
            }
            x += w + 4;
        }
        return h;
    }

    private void card(final int game, float x, float y, float w) {
        String[] g = GAMES[game];
        int color = COLORS[game % COLORS.length];
        boolean on = open == game;
        boolean hover = in(x, y, x + w, y + CARD_H);
        flatRow(x, y, x + w, y + CARD_H, ease("h" + game, hover || on ? 1 : 0), on ? color : 0);
        Draw.round(x + 8, y + 9, x + 24, y + 25, 8, Draw.withAlpha(color, 0x33));
        Draw.icon(PLAY, x + 12, y + 13, 8, color);
        Draw.text(Draw.trim(g[0], (int) w - 40), x + 30, y + 8, TEXT);
        int modes = (g.length - 2) / 2;
        Draw.text(modes == 1 ? "1 mode" : modes + " modes", x + 30, y + 19, MUTED);
        hit(x, y, x + w, y + CARD_H, new Runnable() {
            public void run() {
                open = open == game ? -1 : game;
            }
        });
    }

    /** The open game's lobby button and mode chips, wrapped; returns the bottom edge. */
    private float modes(int game, float l, float y, float r) {
        final String[] g = GAMES[game];
        float x = l;
        int h = 16;
        String lobby = "Lobby";
        float lw = Draw.width(lobby) + 26;
        chip(x, y, lw, h, lobby, LOBBY, MUTED, "/lobby " + g[1]);
        x += lw + 4;
        for (int i = 2; i + 1 < g.length; i += 2) {
            float w = Draw.width(g[i]) + 16;
            if (x + w > r) {
                x = l;
                y += h + 4;
            }
            chip(x, y, w, h, g[i], null, ACCENT, "/play " + g[i + 1]);
            x += w + 4;
        }
        return y + h;
    }

    private void chip(float x, float y, float w, float h, String label, ResourceLocation icon, int color, final String command) {
        boolean hover = in(x, y, x + w, y + h);
        Draw.round(x, y, x + w, y + h, 8, hover ? Draw.withAlpha(color, 0x55) : Draw.withAlpha(color, 0x22));
        Draw.roundOutline(x, y, x + w, y + h, 8, Draw.withAlpha(color, 0x88));
        float tx = x + 8;
        if (icon != null) {
            Draw.icon(icon, tx, y + 4, 8, hover ? TEXT : MUTED);
            tx += 11;
        }
        Draw.text(label, tx, y + 4, hover ? 0xFFFFFFFF : TEXT);
        hit(x, y, x + w, y + h, new Runnable() {
            public void run() {
                GuiQuickplay.this.run(command);
            }
        });
    }
}
