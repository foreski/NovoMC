package com.novomc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

/**
 * Development FPS benchmark, only active with {@code -Dnovomc.benchmark=true}.
 * Builds a lobby-like stress scene (hundreds of armor stands, block entities
 * and constant particles) in a normal world, then measures the same camera
 * view twice: NovoMC's performance mods off, then on. Both runs are uncapped
 * (no VSync, unlimited frame rate) so only rendering cost differs.
 */
public final class DevBenchmark {
    private static final String[] PERFORMANCE = {"entity_culling", "particle_limiter", "performance_tweaks", "sodium"};
    private static final int WARMUP = 100;
    private static final int MEASURE = 300;

    private final Minecraft mc = Minecraft.getMinecraft();
    private int menuTicks;
    private boolean started;
    private int ticks;
    private int phase = -1;
    private int phaseTicks;
    private long frames;
    private long startNanos;
    private final double[] fps = new double[3];

    public static boolean active() {
        return Boolean.getBoolean("novomc.benchmark");
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && phase >= 0 && phaseTicks >= WARMUP) {
            frames++;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!started) {
            if (mc.currentScreen instanceof GuiMainMenu && ++menuTicks > 40) {
                started = true;
                GameSettings gs = mc.gameSettings;
                gs.renderDistanceChunks = 12;
                gs.pauseOnLostFocus = false;
                NovoClient.config().setEnabled("unfocused_fps", false);
                WorldSettings ws = new WorldSettings(1337L, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT);
                ws.enableCommands();
                mc.launchIntegratedServer("novomc-bench-" + System.currentTimeMillis(), "NovoMC Benchmark", ws);
            }
            return;
        }
        if (mc.thePlayer == null) {
            return;
        }
        ticks++;
        if (ticks == 20) {
            command("/gamerule doDaylightCycle false");
            command("/gamerule doMobSpawning false");
            command("/gamerule sendCommandFeedback false");
            command("/time set 6000");
            command("/weather clear");
            command("/tp @p ~ 100 ~");
            mc.thePlayer.capabilities.isFlying = true;
        }
        // Armor stands in a 20x20 grid, 10 to 105 blocks ahead, a row per tick.
        if (ticks >= 40 && ticks < 60) {
            int i = ticks - 40;
            for (int j = 0; j < 20; j++) {
                command(String.format("/summon ArmorStand ~%d ~-2 ~%d {NoGravity:1,ShowArms:1}", -38 + i * 4, 10 + j * 5));
            }
        }
        if (ticks == 62) {
            // Block entities: skulls 20-50 blocks ahead, chests 55-85 blocks ahead.
            command("/fill ~-15 ~-4 ~20 ~15 ~-4 ~50 skull 1");
            command("/fill ~-15 ~-4 ~55 ~15 ~-4 ~85 chest");
        }
        if (ticks == 70) {
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.rotationPitch = 12;
            startPhase(0);
        }
        if (phase >= 0) {
            // Constant particles near and far, like a busy fight.
            command("/particle flame ~ ~-2 ~40 12 4 12 0 120 force");
            command("/particle reddust ~ ~-1 ~8 3 1 3 0 40 force");
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.rotationPitch = 12;
            if (++phaseTicks == WARMUP) {
                frames = 0;
                startNanos = System.nanoTime();
            } else if (phaseTicks == WARMUP + MEASURE) {
                fps[phase] = frames / ((System.nanoTime() - startNanos) / 1e9);
                System.out.println(String.format("[NovoMC] Benchmark %s: %.1f FPS", phase == 0 ? "performance mods OFF" : phase == 1 ? "performance mods ON" : "ON, HUD hidden (HUD cost)",
                        fps[phase]));
                if (phase < 2) {
                    startPhase(phase + 1);
                } else {
                    System.out.println(String.format("[NovoMC] Benchmark result: %.1f -> %.1f FPS (%+.0f%%)", fps[0], fps[1],
                            (fps[1] / fps[0] - 1) * 100));
                    mc.shutdown();
                }
            }
        }
    }

    private void startPhase(int p) {
        phase = p;
        phaseTicks = 0;
        for (String id : PERFORMANCE) {
            NovoClient.config().setEnabled(id, p >= 1);
        }
        NovoClient.apply();
        // Uncapped in both runs, so the comparison is rendering cost alone.
        mc.gameSettings.enableVsync = false;
        Display.setVSyncEnabled(false);
        mc.gameSettings.limitFramerate = (int) GameSettings.Options.FRAMERATE_LIMIT.getValueMax();
        // Vanilla defaults for what Performance Tweaks changes, in the OFF run.
        if (p == 0) {
            mc.gameSettings.clouds = 2;
            mc.gameSettings.entityShadows = true;
        }
        mc.gameSettings.hideGUI = p == 2;
        mc.renderGlobal.loadRenderers();
        // Clear particles left over from the previous run.
        mc.effectRenderer.clearEffects(mc.theWorld);
    }

    private void command(String cmd) {
        mc.thePlayer.sendChatMessage(cmd);
    }
}
