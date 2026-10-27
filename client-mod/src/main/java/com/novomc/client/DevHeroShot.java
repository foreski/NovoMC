package com.novomc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Development helper, only active with {@code -Dnovomc.heroshot=true}: renders
 * the launcher's Home artwork. A 1920x1080 window, no HUD, a sunset over
 * snowy mountains (seed 1234), saved as screenshots/hero.png, then quits.
 */
public final class DevHeroShot {
    private final Minecraft mc = Minecraft.getMinecraft();
    private int menuTicks;
    private boolean started;
    private int ticks;

    public static boolean active() {
        return Boolean.getBoolean("novomc.heroshot");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!started) {
            if (mc.currentScreen instanceof GuiMainMenu && ++menuTicks > 40) {
                started = true;
                try {
                    Display.setDisplayMode(new DisplayMode(1920, 1080));
                } catch (LWJGLException e) {
                    throw new IllegalStateException(e);
                }
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.renderDistanceChunks = 16;
                mc.gameSettings.fancyGraphics = true;
                mc.gameSettings.ambientOcclusion = 2;
                mc.gameSettings.fovSetting = 80;
                mc.gameSettings.clouds = 2;
                // The plain world: no chunk borders or waypoint beams, real time of day, clouds on.
                for (String id : new String[] {"chunk_borders", "waypoints", "time_changer", "weather_changer", "performance_tweaks"}) {
                    NovoClient.config().setEnabled(id, false);
                }
                NovoClient.apply();
                WorldSettings ws = new WorldSettings(1234L, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT);
                ws.enableCommands();
                mc.launchIntegratedServer("novomc-hero-" + System.currentTimeMillis(), "Hero", ws);
            }
            return;
        }
        if (mc.thePlayer == null) {
            return;
        }
        ticks++;
        mc.guiAchievement.clearAchievements();
        if (ticks == 20) {
            mc.thePlayer.sendChatMessage("/gamerule doDaylightCycle false");
            mc.thePlayer.sendChatMessage("/time set 12400");
            mc.thePlayer.sendChatMessage("/weather clear");
            mc.thePlayer.capabilities.isFlying = true;
            mc.gameSettings.hideGUI = true;
        }
        mc.thePlayer.setPositionAndRotation(mc.thePlayer.posX, 100, mc.thePlayer.posZ, 45F, -4F);
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        if (ticks == 400) {
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, "hero.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            System.out.println("[NovoMC] Autotest finished");
            mc.shutdown();
        }
    }
}
