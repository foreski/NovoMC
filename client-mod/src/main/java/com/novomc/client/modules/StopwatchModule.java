package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.module.TextHud;
import net.minecraft.client.gui.GuiScreen;

/** Stopwatch key starts/stops; Shift + key resets. */
public class StopwatchModule extends TextHud {
    private long startedAt;
    private long elapsed;
    private boolean running;

    public StopwatchModule() {
        super("stopwatch");
    }

    @Override
    public void onTick() {
        while (NovoClient.STOPWATCH_KEY.isPressed()) {
            long now = System.currentTimeMillis();
            if (GuiScreen.isShiftKeyDown()) {
                running = false;
                elapsed = 0;
            } else if (running) {
                elapsed += now - startedAt;
                running = false;
            } else {
                startedAt = now;
                running = true;
            }
        }
    }

    @Override
    protected String[] lines(boolean preview) {
        long ms = elapsed + (running ? System.currentTimeMillis() - startedAt : 0);
        return new String[] {String.format("Stopwatch %d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10)};
    }
}
