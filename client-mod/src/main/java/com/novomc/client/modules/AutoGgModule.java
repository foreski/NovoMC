package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Says "gg" in chat when a match ends (Hypixel, PvP Land and similar servers
 * announce the result with one of the lines below). Once per match.
 */
public class AutoGgModule extends Module {
    /** End-of-game lines, matched against the unformatted chat text. */
    private static final String[] TRIGGERS = {
        "1st Killer - ", "1st Place - ", "Winner: ", " - Damage Dealt - ", "Winning Team -", "1st - ", "Winners: ",
        "Winning Team: ", " won the game!", "Top Seeker: ", "1st Place: ", "Last team standing!", "Winner #1 (",
        "Top Survivors", "Winners - ", "Sumo Duel - ", "won the match", "WINNER!", "VICTORY!",
    };
    private static final long COOLDOWN_MS = 15000;

    private long lastSent;
    private int countdown = -1;

    public AutoGgModule() {
        super("auto_gg");
    }

    /** True if this chat line announces the end of a game. */
    public static boolean endOfGame(String plain) {
        for (String t : TRIGGERS) {
            if (plain.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a message is waiting to be sent (for the dev mod check). */
    public boolean pending() {
        return countdown >= 0;
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!isEnabled() || event.type == 2 || countdown >= 0 || System.currentTimeMillis() - lastSent < COOLDOWN_MS) {
            return;
        }
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        // Our own messages must not trigger it again.
        if (mc.thePlayer != null && plain.contains(mc.thePlayer.getName() + ": ")) {
            return;
        }
        if (endOfGame(plain)) {
            countdown = Math.round(number("delay") * 20);
        }
    }

    @Override
    public void onTick() {
        if (countdown < 0 || mc.thePlayer == null) {
            return;
        }
        if (countdown-- == 0) {
            String message = text("message").trim();
            if (!message.isEmpty()) {
                mc.thePlayer.sendChatMessage(message);
            }
            lastSent = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDisable() {
        countdown = -1;
    }
}
