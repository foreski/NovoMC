package com.novomc.client.gui;

import com.novomc.client.trainer.Drill;
import com.novomc.client.trainer.TrainerScores;
import com.novomc.client.trainer.Trainers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;

/** The list of trainers with personal bests; picking one starts it. */
public class GuiTrainers extends GuiNovoSettings {
    public GuiTrainers(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected String title() {
        return "Trainers";
    }

    @Override
    protected String subtitle() {
        return "Practice drills with personal bests. In a drill: R restarts, T comes back here.";
    }

    @Override
    protected String[][] sections() {
        return new String[][] {{"drills", "Drills"}};
    }

    @Override
    protected List<Row> build(String section) {
        List<Row> rows = new ArrayList<Row>();
        final GuiScreen self = this;
        Float cps = TrainerScores.best(GuiClickTest.ID);
        rows.add(new Row("Click Speed", "Click as fast as you can for 10 seconds" + (cps != null ? String.format("  ·  Best %.1f CPS", cps) : "")) {
            protected String value() {
                return "Start";
            }

            protected void click() {
                mc.displayGuiScreen(new GuiClickTest(self));
            }
        });
        for (final Drill drill : Trainers.drills()) {
            Float best = TrainerScores.best(drill.id);
            rows.add(new Row(drill.name, drill.description + (best != null ? "  ·  Best " + drill.format(best) : "")) {
                protected String value() {
                    return "Start";
                }

                protected void click() {
                    Trainers.INSTANCE.start(drill.id);
                }
            });
        }
        return rows;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
