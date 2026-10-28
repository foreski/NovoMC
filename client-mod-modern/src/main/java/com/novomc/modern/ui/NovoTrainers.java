package com.novomc.modern.ui;

import com.novomc.modern.trainer.Drill;
import com.novomc.modern.trainer.Drills;
import com.novomc.modern.trainer.Trainers;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Trainers screen on modern Minecraft: each drill with its personal best and a Start button. */
public class NovoTrainers extends NovoPanel {
    private static final int ROW = 40;

    public NovoTrainers(Screen parent) {
        super(parent, "Trainers");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int[] p = beginFrame(g, mouseX, mouseY);
        int l = p[0];
        int r = p[2];
        header(g, l, r, p[1] + 4, "Trainers", "Practice drills in a private world  ·  R retries, G opens this list");
        int top = p[1] + 62;
        int bottom = p[3] - PAD;
        List<Drill> drills = Drills.all();
        float max = updateScroll(drills.size() * (ROW + 4f), bottom - top);
        clip(g, l - 2, top, r + 8, bottom);
        int y = Math.round(top - scroll);
        for (Drill d : drills) {
            boolean hover = in(l, y, r, y + ROW);
            NovoUi.round(g, l, y, r, y + ROW, hover ? 0x1AFFFFFF : 0x0DFFFFFF);
            NovoUi.round(g, l + 8, y + 8, l + 32, y + 32, 0x2E5B8CFF);
            NovoUi.icon(g, d.icon, l + 14, y + 14, 12, NovoUi.ACCENT);
            Component name = NovoUi.bold(d.name);
            NovoUi.draw(g, name, l + 40, y + 9, NovoUi.TEXT);
            Float best = Trainers.best(d.id);
            String pb = best == null ? "No best yet" : "Best: " + d.format(best);
            NovoUi.draw(g, NovoUi.text(pb), l + 48 + NovoUi.width(name), y + 9, best == null ? NovoUi.MUTED : 0xFFFACC15);
            String desc = d.description;
            while (NovoUi.width(NovoUi.text(desc)) > r - 80 - (l + 40) && desc.length() > 4) {
                desc = desc.substring(0, desc.length() - 4) + "…";
            }
            NovoUi.draw(g, NovoUi.text(desc), l + 40, y + 22, NovoUi.MUTED);
            boolean bh = in(r - 70, y + 11, r - 8, y + 29);
            NovoUi.round(g, r - 70, y + 11, r - 8, y + 29, bh ? 0xFF6E9BFF : NovoUi.ACCENT);
            NovoUi.centered(g, NovoUi.text("Start"), r - 39, y + 16, 0xFFFFFFFF);
            String id = d.id;
            hit(r - 70, y + 11, r - 8, y + 29, () -> Trainers.start(id));
            y += ROW + 4;
        }
        noClip(g);
        scrollbar(g, r, top, bottom, max);
    }
}
