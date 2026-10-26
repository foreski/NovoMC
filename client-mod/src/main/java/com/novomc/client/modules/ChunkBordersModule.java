package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.util.Draw3D;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Outlines your chunk (grid on its walls) and marks the corners of its neighbors. */
public class ChunkBordersModule extends Module {
    public ChunkBordersModule() {
        super("chunk_borders");
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Entity view = mc.getRenderViewEntity();
        if (!isEnabled() || mc.theWorld == null || view == null) {
            return;
        }
        double[] cam = Draw3D.camera(view, event.partialTicks);
        int cx = MathHelper.floor_double(view.posX) >> 4;
        int cz = MathHelper.floor_double(view.posZ) >> 4;
        int own = color("color") | 0xFF000000;
        int grid = (own & 0xFFFFFF) | 0xB0000000;
        int neighbor = 0xC0FF5555;
        double bottom = -cam[1];
        double top = 256 - cam[1];

        Draw3D.begin(2.5f);
        WorldRenderer w = Draw3D.lines();
        // Corner pillars of the 3x3 chunks around you; your chunk's in your color.
        for (int i = -1; i <= 2; i++) {
            for (int j = -1; j <= 2; j++) {
                double x = (cx + i) * 16 - cam[0];
                double z = (cz + j) * 16 - cam[2];
                boolean mine = (i == 0 || i == 1) && (j == 0 || j == 1);
                Draw3D.line(w, x, bottom, z, x, top, z, mine ? own : neighbor);
            }
        }
        // Grid on your chunk's walls: vertical lines every 4 blocks, rings every 8.
        double x0 = cx * 16 - cam[0], x1 = x0 + 16, z0 = cz * 16 - cam[2], z1 = z0 + 16;
        for (int k = 4; k < 16; k += 4) {
            Draw3D.line(w, x0 + k, bottom, z0, x0 + k, top, z0, grid);
            Draw3D.line(w, x0 + k, bottom, z1, x0 + k, top, z1, grid);
            Draw3D.line(w, x0, bottom, z0 + k, x0, top, z0 + k, grid);
            Draw3D.line(w, x1, bottom, z0 + k, x1, top, z0 + k, grid);
        }
        for (int y = 0; y <= 256; y += 8) {
            double yy = y - cam[1];
            Draw3D.line(w, x0, yy, z0, x1, yy, z0, grid);
            Draw3D.line(w, x0, yy, z1, x1, yy, z1, grid);
            Draw3D.line(w, x0, yy, z0, x0, yy, z1, grid);
            Draw3D.line(w, x1, yy, z0, x1, yy, z1, grid);
        }
        Draw3D.drawLines();
        Draw3D.end();
    }
}
