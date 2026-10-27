package com.novomc.client;

import com.google.gson.JsonPrimitive;
import com.novomc.client.gui.GuiNovoVideo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Development check for the rendering features and the Video Settings screen,
 * only active with {@code -Dnovomc.features=true}: builds glass walls, a grass
 * slope and snow around plants, turns every feature on, screenshots the scene
 * and each Video Settings section, and quits. First it screenshots the themed
 * vanilla menus (servers, worlds, Options, Controls, Language).
 */
public final class DevFeatureTest {
    private static final String[] FEATURES = {"sodium", "fast_math", "smart_animations", "lazy_chunks",
        "connected_textures", "custom_sky", "better_grass", "better_snow", "video_settings", "custom_menus"};

    private final Minecraft mc = Minecraft.getMinecraft();
    private int menuTicks;
    private boolean started;
    private int ticks;
    private GuiNovoVideo video;

    public static boolean active() {
        return Boolean.getBoolean("novomc.features");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!started) {
            menuTicks++;
            switch (menuTicks) {
                case 40: mc.displayGuiScreen(new net.minecraft.client.gui.GuiMultiplayer(new GuiMainMenu())); break;
                case 50: shot("menu-multiplayer"); mc.displayGuiScreen(new net.minecraft.client.gui.GuiSelectWorld(new GuiMainMenu())); break;
                case 60: shot("menu-worlds"); mc.displayGuiScreen(new net.minecraft.client.gui.GuiOptions(new GuiMainMenu(), mc.gameSettings)); break;
                case 70: shot("menu-options"); mc.displayGuiScreen(new net.minecraft.client.gui.GuiControls(new GuiMainMenu(), mc.gameSettings)); break;
                case 80: shot("menu-controls"); mc.displayGuiScreen(new net.minecraft.client.gui.GuiLanguage(new GuiMainMenu(), mc.gameSettings, mc.getLanguageManager())); break;
                case 90: shot("menu-language"); mc.displayGuiScreen(new GuiMainMenu()); break;
                default: break;
            }
            if (menuTicks > 100) {
                started = true;
                mc.gameSettings.pauseOnLostFocus = false;
                WorldSettings ws = new WorldSettings(42L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
                ws.enableCommands();
                mc.launchIntegratedServer("novomc-features-" + System.currentTimeMillis(), "NovoMC Features", ws);
            }
            return;
        }
        if (mc.thePlayer == null) {
            return;
        }
        ticks++;
        switch (ticks) {
            case 20:
                for (String id : FEATURES) {
                    NovoClient.config().setEnabled(id, true);
                }
                NovoClient.config().setSetting("better_grass", "mode", new JsonPrimitive("fast"));
                NovoClient.apply();
                for (String cmd : new String[] {
                    "/gamerule doDaylightCycle false", "/time set 6000", "/weather clear", "/gamerule doMobSpawning false",
                    // Glass walls: clear and red stained.
                    "/fill ~-7 ~ ~6 ~-2 ~3 ~6 glass", "/fill ~2 ~ ~6 ~7 ~3 ~6 stained_glass 14",
                    // A raised grass terrace, so its sides show.
                    "/fill ~-2 ~ ~9 ~2 ~1 ~11 grass",
                    // Snow on the ground with plants and a fence standing in it.
                    "/fill ~-5 ~ ~3 ~5 ~ ~3 snow_layer", "/setblock ~-3 ~ ~4 tallgrass 1", "/setblock ~0 ~ ~4 red_flower",
                    "/setblock ~3 ~ ~4 fence"}) {
                    mc.thePlayer.sendChatMessage(cmd);
                }
                break;
            case 30:
                mc.thePlayer.capabilities.isFlying = true;
                mc.thePlayer.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY + 1.5, mc.thePlayer.posZ - 2, 0, 20);
                break;
            case 80:
                mc.thePlayer.rotationYaw = 0;
                mc.thePlayer.rotationPitch = 20;
                shot("features-world");
                break;
            case 90:
                video = new GuiNovoVideo(null);
                mc.displayGuiScreen(video);
                break;
            case 100:
                shot("video-display");
                video.showSection("quality");
                break;
            case 110:
                shot("video-quality");
                video.showSection("performance");
                break;
            case 120:
                shot("video-performance");
                mc.displayGuiScreen(null);
                System.out.println("[NovoMC] Autotest finished");
                mc.shutdown();
                break;
            default:
                break;
        }
    }

    private void shot(String name) {
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        System.out.println("[NovoMC] Feature test screenshot " + name);
    }
}
