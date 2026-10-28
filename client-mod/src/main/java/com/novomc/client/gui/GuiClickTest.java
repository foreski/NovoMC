package com.novomc.client.gui;

import com.novomc.client.trainer.TrainerScores;
import com.novomc.client.util.Draw;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

/**
 * Click Speed trainer: click as fast as you can for ten seconds. Scores the
 * average CPS (the personal best) and the best single second. No world needed.
 */
public class GuiClickTest extends GuiScreen {
    public static final String ID = "cps";
    private static final long LENGTH_MS = 10_000;

    private final GuiScreen parent;
    private final List<Long> clicks = new ArrayList<Long>();
    private long startedAt;
    private boolean done;
    private boolean newBest;
    private float average;
    private int peak;

    public GuiClickTest(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        if (button != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (done) {
            if (now - startedAt > LENGTH_MS + 800) {
                // Click again to retry, after a short pause so the last frantic clicks don't restart it.
                done = false;
                clicks.clear();
                startedAt = 0;
            }
            return;
        }
        if (startedAt == 0) {
            startedAt = now;
        }
        clicks.add(now);
    }

    @Override
    public void updateScreen() {
        if (!done && startedAt != 0 && System.currentTimeMillis() - startedAt >= LENGTH_MS) {
            done = true;
            List<Long> counted = new ArrayList<Long>();
            for (long c : clicks) {
                if (c - startedAt < LENGTH_MS) {
                    counted.add(c);
                }
            }
            average = counted.size() / (LENGTH_MS / 1000f);
            peak = 0;
            for (int i = 0; i < counted.size(); i++) {
                int n = 0;
                for (int j = i; j < counted.size() && counted.get(j) - counted.get(i) < 1000; j++) {
                    n++;
                }
                peak = Math.max(peak, n);
            }
            newBest = TrainerScores.record(ID, average, false);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;
        Draw.spaced("CLICK SPEED", cx - Draw.spacedWidth("CLICK SPEED", 1.5f) / 2f, cy - 70, 0xFF22D3EE, 1.5f);

        String big;
        String sub;
        if (done) {
            big = String.format("%.1f CPS", average);
            sub = (newBest ? "New best!  ·  " : "") + "Peak " + peak + " CPS in one second";
        } else if (startedAt == 0) {
            big = "Click to start";
            sub = "Click as fast as you can for 10 seconds";
        } else {
            long left = LENGTH_MS - (System.currentTimeMillis() - startedAt);
            big = String.valueOf(clicks.size());
            sub = String.format("%.1f s left", Math.max(0, left) / 1000f);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy - 40, 0);
        GlStateManager.scale(3, 3, 1);
        Draw.text(big, -Draw.width(big) / 2f, 0, done && newBest ? 0xFF4ADE80 : 0xFFFFFFFF);
        GlStateManager.popMatrix();
        Draw.text(sub, cx - Draw.width(sub) / 2f, cy, 0xFFB9CBFF);

        Float best = TrainerScores.best(ID);
        String footer = (best != null ? String.format("Best: %.1f CPS   ·   ", best) : "") + (done ? "Click to retry   ·   " : "") + "Esc to go back";
        Draw.text(footer, cx - Draw.width(footer) / 2f, height - 24, 0xFF8A93A6);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
