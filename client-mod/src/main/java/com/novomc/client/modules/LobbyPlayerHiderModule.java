package com.novomc.client.modules;

import com.novomc.client.module.Module;
import java.util.Collection;
import java.util.Locale;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

/** Hides other players' models only when a crowded lobby is explicitly identified by its sidebar. */
public class LobbyPlayerHiderModule extends Module {
    private static volatile boolean enabled;
    private static volatile boolean crowdedLobby;
    private static volatile int minimumPlayers = 12;
    private int tickCounter;

    public LobbyPlayerHiderModule() {
        super("lobby_player_hider");
    }

    @Override
    protected void onEnable() {
        onSettingsChanged();
        refreshLobbyState();
    }

    @Override
    public void onSettingsChanged() {
        minimumPlayers = Math.max(2, Math.min(80, integer("minimumPlayers")));
        enabled = true;
    }

    @Override
    protected void onDisable() {
        enabled = false;
        crowdedLobby = false;
    }

    @Override
    public void onTick() {
        if (++tickCounter >= 10) {
            tickCounter = 0;
            refreshLobbyState();
        }
    }

    /** True only for remote player models while a populated lobby scoreboard is present. */
    public static boolean hide(Entity entity) {
        return enabled && crowdedLobby && entity instanceof EntityPlayer
                && entity != mcViewEntity();
    }

    private static Entity mcViewEntity() {
        return net.minecraft.client.Minecraft.getMinecraft().getRenderViewEntity();
    }

    private void refreshLobbyState() {
        if (mc.theWorld == null || mc.thePlayer == null || mc.isSingleplayer()
                || mc.getCurrentServerData() == null
                || mc.theWorld.playerEntities.size() < minimumPlayers) {
            crowdedLobby = false;
            return;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            crowdedLobby = false;
            return;
        }

        StringBuilder sidebar = new StringBuilder(objective.getDisplayName());
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        for (Score score : scores) {
            String name = score.getPlayerName();
            sidebar.append('\n').append(ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(name), name));
        }
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(sidebar.toString())
                .replace("\u200B", "").toUpperCase(Locale.ROOT);
        boolean lobbyMarker = text.contains("LOBBY")
                || text.contains("WAITING FOR PLAYERS")
                || text.contains("WAITING TO START")
                || text.contains("STARTING IN");
        crowdedLobby = lobbyMarker;
    }
}
