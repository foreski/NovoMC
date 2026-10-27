package com.novomc.sodium;

import com.novomc.client.sodium.SodiumRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

/**
 * The Sodium Renderer on its own, without the rest of NovoMC. Do not install it
 * next to the NovoMC Client mod, which already contains it.
 */
@Mod(modid = "novomc_sodium", name = "NovoMC Sodium", version = "0.1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class NovoSodiumMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Chunk drawing needs VBOs; vanilla leaves them off by default.
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenGlHelper.vboSupported) {
            mc.gameSettings.useVbo = true;
        }
        SodiumRenderer.setEnabled(true);
        if (Boolean.getBoolean("novomc.sodium.autotest")) {
            MinecraftForge.EVENT_BUS.register(new AutoTest());
        }
    }

    /**
     * Development check ({@code -Dnovomc.sodium.autotest=true}): opens a normal
     * world, then measures and screenshots the same view with the Sodium
     * Renderer on and off, and quits.
     */
    public static final class AutoTest {
        private static final int SETTLE = 200;
        private static final int MEASURE = 300;
        private final Minecraft mc = Minecraft.getMinecraft();
        private int menuTicks;
        private boolean started;
        private int ticks;
        private long frames;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (!started) {
                if (mc.currentScreen instanceof GuiMainMenu && ++menuTicks > 60) {
                    started = true;
                    // Keep measuring if the window loses focus.
                    mc.gameSettings.pauseOnLostFocus = false;
                    mc.gameSettings.enableVsync = false;
                    Display.setVSyncEnabled(false);
                    mc.gameSettings.limitFramerate = 260;
                    mc.gameSettings.renderDistanceChunks = 12;
                    mc.launchIntegratedServer("sodium-test", "Sodium Test", new WorldSettings(1234L,
                            WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT));
                }
                return;
            }
            if (mc.thePlayer == null) {
                return;
            }
            ticks++;
            if (ticks == 1) {
                mc.thePlayer.sendChatMessage("/gamerule doDaylightCycle false");
                mc.thePlayer.sendChatMessage("/time set 6000");
                mc.thePlayer.capabilities.isFlying = true;
            }
            // Fixed camera: hover above spawn looking across the terrain.
            mc.thePlayer.setPositionAndRotation(mc.thePlayer.posX, 100, mc.thePlayer.posZ, 45F, 15F);
            mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
            run(SETTLE, "on");
            run(SETTLE * 2 + MEASURE, "off");
            if (ticks == SETTLE + MEASURE) {
                SodiumRenderer.setEnabled(false);
            } else if (ticks == 2 * (SETTLE + MEASURE)) {
                // The launch test waits for this line.
                System.out.println("[NovoMC Sodium] Autotest finished");
                mc.shutdown();
            }
        }

        /** From tick {@code start}, counts frames for MEASURE ticks, then logs FPS and screenshots. */
        private void run(int start, String label) {
            if (ticks == start) {
                frames = 0;
                System.out.println("[NovoMC Sodium] " + label + ": " + (SodiumRenderer.isActive() ? SodiumRenderer.debugLine() : "vanilla"));
            } else if (ticks > start && ticks <= start + MEASURE) {
                frames += Minecraft.getDebugFPS();
                if (ticks == start + MEASURE) {
                    System.out.printf("[NovoMC Sodium] %s: %.1f FPS%n", label, frames / (float) MEASURE);
                    ScreenShotHelper.saveScreenshot(mc.mcDataDir, "sodium-" + label + ".png",
                            mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                }
            }
        }
    }
}
