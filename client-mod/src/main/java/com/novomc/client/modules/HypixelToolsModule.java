package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.module.Module;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.Display;

/**
 * Hypixel tools: requeue into the same game (automatically or with a key),
 * hide lobby join and Watchdog spam, a sound when a game starts or ends while
 * the window is in the background, and one-key /wdr reports.
 */
public class HypixelToolsModule extends Module {
    private static final String[] WATCHDOG = {
        "[WATCHDOG ANNOUNCEMENT]", "Watchdog has banned", "Staff have banned", "Blacklisted modifications are a bannable offense",
        "Watchdog has detected and banned",
    };

    /** The last "/play <mode>" Hypixel offered ("Click here to play again"). */
    private String lastGame;
    private int requeueIn = -1;

    public HypixelToolsModule() {
        super("hypixel_tools");
    }

    public String lastGame() {
        return lastGame;
    }

    /** True for "[MVP+] Name joined the lobby!" and its variants ("slid into the lobby!", ...). */
    public static boolean lobbyJoin(String plain) {
        String s = plain.trim();
        if (s.startsWith(">>>") && s.endsWith("<<<")) {
            s = s.substring(3, s.length() - 3).trim();
        }
        return s.endsWith(" the lobby!") && !s.contains(":");
    }

    public static boolean watchdog(String plain) {
        for (String w : WATCHDOG) {
            if (plain.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** The "/play ..." command in a chat line's click events, or null. */
    public static String playCommand(IChatComponent c) {
        ClickEvent click = c.getChatStyle().getChatClickEvent();
        if (click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND && click.getValue().startsWith("/play ")) {
            return click.getValue();
        }
        for (Object sibling : c.getSiblings()) {
            String found = playCommand((IChatComponent) sibling);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!isEnabled() || event.type == 2) {
            return;
        }
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        if ((bool("hideLobbyJoins") && lobbyJoin(plain)) || (bool("hideWatchdog") && watchdog(plain))) {
            event.setCanceled(true);
            return;
        }
        String play = playCommand(event.message);
        if (play != null) {
            lastGame = play;
            if (bool("autoRequeue") && requeueIn < 0) {
                requeueIn = Math.round(number("requeueDelay") * 20);
            }
        }
        if (bool("gameAlerts") && !Display.isActive()
                && (plain.startsWith("The game starts in 1 second!") || AutoGgModule.endOfGame(plain))) {
            ding();
        }
    }

    private void ding() {
        if (mc.thePlayer != null) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.0F));
        }
    }

    @Override
    public void onTick() {
        if (mc.thePlayer == null) {
            requeueIn = -1;
            return;
        }
        if (requeueIn >= 0 && requeueIn-- == 0 && lastGame != null) {
            mc.thePlayer.sendChatMessage(lastGame);
        }
        while (NovoClient.REQUEUE_KEY.isPressed()) {
            if (lastGame != null) {
                mc.thePlayer.sendChatMessage(lastGame);
            } else {
                say(EnumChatFormatting.GRAY + "No game to requeue yet. Play one first.");
            }
        }
        while (NovoClient.REPORT_KEY.isPressed()) {
            MovingObjectPosition hit = mc.objectMouseOver;
            if (hit != null && hit.entityHit instanceof EntityPlayer) {
                report(hit.entityHit.getName());
            } else {
                say(EnumChatFormatting.GRAY + "Look at a player to report them.");
            }
        }
    }

    private void report(String name) {
        mc.thePlayer.sendChatMessage("/wdr " + name);
    }

    private void say(String text) {
        mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[NovoMC] " + EnumChatFormatting.RESET + text));
    }

    @Override
    protected void onDisable() {
        requeueIn = -1;
    }

    /** /qr <player>: quick Watchdog report. */
    public static final class ReportCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return "qr";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/qr <player>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
            return args.length == 1 ? getListOfStringsMatchingLastWord(args,
                    Minecraft.getMinecraft().getNetHandler() == null ? new String[0] : names()) : null;
        }

        private static String[] names() {
            List<String> out = new ArrayList<String>();
            for (NetworkPlayerInfo info : Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()) {
                out.add(info.getGameProfile().getName());
            }
            return out.toArray(new String[0]);
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length == 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /qr <player>"));
                return;
            }
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/wdr " + args[0]);
        }
    }
}
