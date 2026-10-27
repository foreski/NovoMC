package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.module.Module;
import net.minecraft.client.settings.KeyBinding;

/**
 * Auto Text: three key bindings (Controls > NovoMC, unbound by default) that
 * each send a chat message or command from the module's settings.
 */
public class AutoTextModule extends Module {
    private static final long MIN_GAP_MS = 1500;
    private long lastSent;
    /** Messages sent, for the dev mod check. */
    public int sent;

    public AutoTextModule() {
        super("auto_text");
    }

    @Override
    public void onTick() {
        KeyBinding[] keys = NovoClient.AUTO_TEXT_KEYS;
        for (int i = 0; i < keys.length; i++) {
            // Drain presses even while a screen is open, so they don't fire later.
            boolean pressed = keys[i].isPressed();
            if (pressed && mc.currentScreen == null && mc.thePlayer != null) {
                send(text("message" + (i + 1)));
            }
        }
    }

    private void send(String message) {
        String m = message.trim();
        long now = System.currentTimeMillis();
        // A small gap so a held key can't spam the server (and get you kicked).
        if (m.isEmpty() || now - lastSent < MIN_GAP_MS) {
            return;
        }
        lastSent = now;
        sent++;
        mc.thePlayer.sendChatMessage(m);
    }
}
