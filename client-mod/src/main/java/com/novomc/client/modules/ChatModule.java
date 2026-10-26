package com.novomc.client.modules;

import com.novomc.client.module.Module;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Timestamps and stacking: a repeat of the previous message replaces it with
 * "(x2)", "(x3)"... using chat line IDs, which vanilla deletes and redraws.
 */
public class ChatModule extends Module {
    /** Chat line IDs NovoMC uses; anything else in a 16-bit window above this is ours. */
    private static final int BASE_ID = 0x4E4F0000;

    private int nextId;
    private String lastText;
    private int lastId;
    private int count;

    public ChatModule() {
        super("chat");
    }

    private static ChatStyle gray() {
        return new ChatStyle().setColor(EnumChatFormatting.GRAY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChat(ClientChatReceivedEvent event) {
        // Type 2 is the action bar above the hotbar.
        if (!isEnabled() || event.type == 2 || event.isCanceled()) {
            return;
        }
        boolean stack = bool("stack");
        boolean stamps = bool("timestamps");
        if (!stack && !stamps) {
            return;
        }
        IChatComponent message = event.message;
        String text = message.getUnformattedText();
        int id = 0;
        if (stack) {
            if (text.equals(lastText)) {
                count++;
            } else {
                count = 1;
                lastText = text;
                lastId = BASE_ID + (nextId++ & 0xFFFF);
            }
            id = lastId;
        }

        IChatComponent out = new ChatComponentText("");
        if (stamps) {
            String pattern = bool("seconds") ? "HH:mm:ss" : "HH:mm";
            out.appendSibling(new ChatComponentText("[" + LocalTime.now().format(DateTimeFormatter.ofPattern(pattern)) + "] ").setChatStyle(gray()));
        }
        out.appendSibling(message);
        if (count > 1) {
            out.appendSibling(new ChatComponentText(" (x" + count + ")").setChatStyle(gray()));
        }
        event.setCanceled(true);
        mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(out, id);
    }
}
