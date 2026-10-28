package com.novomc.modern.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Chat: stacking replaces the previous copy of a repeated message. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccess {
    @Accessor("allMessages")
    List<GuiMessage> novomc$allMessages();

    @Invoker("refreshTrimmedMessages")
    void novomc$refreshTrimmedMessages();
}
