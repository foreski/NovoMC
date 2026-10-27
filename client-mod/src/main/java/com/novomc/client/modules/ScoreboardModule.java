package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

/** The sidebar scoreboard as a movable HUD module (vanilla's is hidden by MixinGuiIngame). */
public class ScoreboardModule extends HudModule {
    private static final int LINE = 9;
    private static final int PAD = 2;
    private static volatile boolean on;

    public ScoreboardModule() {
        super("scoreboard");
    }

    public static boolean replacesVanilla() {
        return on;
    }

    @Override
    protected void onEnable() {
        on = true;
    }

    @Override
    protected void onDisable() {
        on = false;
    }

    /** The objective shown in the sidebar, using vanilla's team-color slot rules. */
    private ScoreObjective objective() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScorePlayerTeam team = sb.getPlayersTeam(mc.thePlayer.getName());
        if (team != null) {
            int slot = team.getChatFormat().getColorIndex();
            if (slot >= 0) {
                ScoreObjective o = sb.getObjectiveInDisplaySlot(3 + slot);
                if (o != null) {
                    return o;
                }
            }
        }
        return sb.getObjectiveInDisplaySlot(1);
    }

    /** {title, then "name\0points" rows top to bottom}. */
    private List<String> rows(boolean preview) {
        List<String> out = new ArrayList<String>();
        ScoreObjective objective = objective();
        if (objective == null) {
            if (preview) {
                out.add(EnumChatFormatting.BOLD + "NOVO CLIENT");
                out.add("Kills: " + EnumChatFormatting.GREEN + "12\u00003");
                out.add("Wins: " + EnumChatFormatting.GOLD + "4\u00002");
                out.add(EnumChatFormatting.YELLOW + "play.example.net\u00001");
            }
            return out;
        }
        Scoreboard sb = objective.getScoreboard();
        List<Score> scores = new ArrayList<Score>();
        Collection<Score> sorted = sb.getSortedScores(objective);
        for (Score s : sorted) {
            if (s.getPlayerName() != null && !s.getPlayerName().startsWith("#")) {
                scores.add(s);
            }
        }
        if (scores.size() > 15) {
            scores = scores.subList(scores.size() - 15, scores.size());
        }
        out.add(objective.getDisplayName());
        for (int i = scores.size() - 1; i >= 0; i--) {
            Score s = scores.get(i);
            out.add(ScorePlayerTeam.formatPlayerName(sb.getPlayersTeam(s.getPlayerName()), s.getPlayerName()) + "\u0000" + s.getScorePoints());
        }
        return out;
    }

    private String points(String row) {
        int i = row.indexOf('\u0000');
        return i < 0 ? "" : EnumChatFormatting.RED + row.substring(i + 1);
    }

    private String name(String row) {
        int i = row.indexOf('\u0000');
        return i < 0 ? row : row.substring(0, i);
    }

    @Override
    public boolean visible() {
        return objective() != null;
    }

    @Override
    public int width(boolean preview) {
        List<String> rows = rows(preview);
        int w = 0;
        boolean numbers = bool("numbers");
        for (int i = 0; i < rows.size(); i++) {
            String r = rows.get(i);
            int rw = Draw.width(name(r)) + (numbers && i > 0 ? Draw.width(" " + points(r)) : 0);
            w = Math.max(w, rw);
        }
        return w + PAD * 2;
    }

    @Override
    public int height(boolean preview) {
        return Math.max(1, rows(preview).size()) * LINE + 2;
    }

    @Override
    public void render(boolean preview) {
        List<String> rows = rows(preview);
        if (rows.isEmpty()) {
            return;
        }
        int w = width(preview);
        int alpha = Math.round(number("background") / 100f * 255);
        boolean shadow = bool("shadow");
        boolean numbers = bool("numbers");

        Draw.rect(0, 0, w, LINE + 1, Draw.withAlpha(0, Math.min(255, alpha + 24)));
        Draw.rect(0, LINE + 1, w, height(preview), Draw.withAlpha(0, alpha));
        String title = rows.get(0);
        Draw.text(title, (w - Draw.width(title)) / 2f, 1, 0xFFFFFFFF, shadow);
        for (int i = 1; i < rows.size(); i++) {
            int y = 2 + i * LINE;
            Draw.text(name(rows.get(i)), PAD, y, 0xFFFFFFFF, shadow);
            if (numbers) {
                String p = points(rows.get(i));
                Draw.text(p, w - PAD - Draw.width(p), y, 0xFFFFFFFF, shadow);
            }
        }
    }
}
