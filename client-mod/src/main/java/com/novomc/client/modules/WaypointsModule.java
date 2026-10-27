package com.novomc.client.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.novomc.client.NovoClient;
import com.novomc.client.gui.GuiWaypoints;
import com.novomc.client.module.Module;
import com.novomc.client.util.Draw;
import com.novomc.client.util.Draw3D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

/**
 * Waypoints: B marks where you stand, M lists them (Controls > NovoMC to
 * change keys). Each shows a colored beam and its name and distance, through
 * walls. Saved per server (or singleplayer world) and dimension in
 * config/novomc/waypoints.json.
 */
public class WaypointsModule extends Module {
    public static final int[] PALETTE = {0xFF5B8CFF, 0xFFB45BFF, 0xFF22D3EE, 0xFF4ADE80, 0xFFFACC15, 0xFFF87171, 0xFFFB923C, 0xFFFFFFFF};
    /** Labels farther than this are drawn at this distance (scaled) so they stay inside the render range. */
    private static final double LABEL_RANGE = 24;

    public static final class Waypoint {
        public String name;
        public int x, y, z, dim;
        public int color;
        public boolean visible = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, List<Waypoint>> all;

    public WaypointsModule() {
        super("waypoints");
    }

    private File file() {
        return new File(mc.mcDataDir, "config/novomc/waypoints.json");
    }

    private Map<String, List<Waypoint>> all() {
        if (all == null) {
            all = new LinkedHashMap<String, List<Waypoint>>();
            try {
                if (file().exists()) {
                    Map<String, List<Waypoint>> read = GSON.fromJson(new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8),
                            new TypeToken<LinkedHashMap<String, List<Waypoint>>>() {}.getType());
                    if (read != null) {
                        all.putAll(read);
                    }
                }
            } catch (Exception e) {
                System.err.println("[NovoMC] Couldn't read waypoints: " + e);
            }
        }
        return all;
    }

    public void save() {
        try {
            file().getParentFile().mkdirs();
            Files.write(file().toPath(), GSON.toJson(all()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't save waypoints: " + e);
        }
    }

    /** Which server or world the player is in. */
    public String worldKey() {
        if (mc.getCurrentServerData() != null) {
            return "server:" + mc.getCurrentServerData().serverIP.toLowerCase();
        }
        if (mc.getIntegratedServer() != null) {
            return "world:" + mc.getIntegratedServer().getFolderName();
        }
        return "unknown";
    }

    /** This server's/world's waypoints (all dimensions); edit, then call save(). */
    public List<Waypoint> here() {
        List<Waypoint> list = all().get(worldKey());
        if (list == null) {
            list = new ArrayList<Waypoint>();
            all().put(worldKey(), list);
        }
        return list;
    }

    /** Adds a waypoint at the player's feet. */
    public Waypoint addHere() {
        List<Waypoint> list = here();
        Waypoint w = new Waypoint();
        w.name = "Waypoint " + (list.size() + 1);
        w.x = MathHelper.floor_double(mc.thePlayer.posX);
        w.y = MathHelper.floor_double(mc.thePlayer.posY);
        w.z = MathHelper.floor_double(mc.thePlayer.posZ);
        w.dim = mc.thePlayer.dimension;
        w.color = PALETTE[list.size() % PALETTE.length];
        list.add(w);
        save();
        return w;
    }

    @Override
    public void onTick() {
        if (mc.thePlayer == null) {
            return;
        }
        if (NovoClient.WAYPOINT_ADD_KEY.isPressed() && mc.currentScreen == null) {
            Waypoint w = addHere();
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[NovoMC] " + EnumChatFormatting.RESET
                    + "Added " + w.name + " at " + w.x + ", " + w.y + ", " + w.z + EnumChatFormatting.GRAY + " (open the list with "
                    + Keyboard.getKeyName(NovoClient.WAYPOINTS_KEY.getKeyCode()) + ")"));
        }
        if (NovoClient.WAYPOINTS_KEY.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiWaypoints(null));
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Entity view = mc.getRenderViewEntity();
        if (!isEnabled() || mc.theWorld == null || view == null || mc.thePlayer == null) {
            return;
        }
        double[] cam = Draw3D.camera(view, event.partialTicks);
        for (Waypoint w : here()) {
            if (!w.visible || w.dim != mc.thePlayer.dimension) {
                continue;
            }
            double dx = w.x + 0.5 - cam[0], dy = w.y - cam[1], dz = w.z + 0.5 - cam[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (bool("beams")) {
                beam(dx, dz, cam[1], w.color);
            }
            label(w, dx, dy + 1.2, dz, dist);
        }
    }

    private void beam(double dx, double dz, double camY, int color) {
        Draw3D.begin(2f);
        GlStateManager.disableDepth();
        Draw3D.color(color & 0xFFFFFF, 0.22f);
        Draw3D.filledBox(new AxisAlignedBB(dx - 0.15, -camY, dz - 0.15, dx + 0.15, 256 - camY, dz + 0.15));
        GlStateManager.enableDepth();
        Draw3D.end();
    }

    private void label(Waypoint w, double x, double y, double z, double dist) {
        double shown = Math.min(dist, LABEL_RANGE);
        double k = dist > 0 ? shown / dist : 1;
        float scale = (float) Math.max(0.02, shown * 0.004);
        String text = w.name + (bool("distance") ? EnumChatFormatting.GRAY + " " + Math.round(dist) + "m" : "");
        RenderManager rm = mc.getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x * k, y * k, z * k);
        GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
        GlStateManager.rotate(mc.gameSettings.thirdPersonView == 2 ? -rm.playerViewX : rm.playerViewX, 1, 0, 0);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int half = Draw.width(text) / 2;
        Draw.rect(-half - 4, -3, half + 4, 11, 0x90000000);
        Draw.rect(-half - 4, 10, half + 4, 11, w.color);
        Draw.text(text, -half, 0, 0xFFFFFFFF);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }
}
