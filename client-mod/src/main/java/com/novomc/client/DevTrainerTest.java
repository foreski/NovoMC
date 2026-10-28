package com.novomc.client;

import com.novomc.client.gui.GuiClickTest;
import com.novomc.client.gui.GuiTrainers;
import com.novomc.client.modules.AutoProfilesModule;
import com.novomc.client.module.Module;
import com.novomc.client.trainer.TrainerScores;
import com.novomc.client.trainer.Trainers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Development check for the trainers and Auto Mode Profiles, only active with
 * {@code -Dnovomc.trainers=true}: opens the Trainers list and Click Speed,
 * runs Aim & Combo with scripted attacks, Speedbridge and Pot Refill with
 * scripted refills, checks the Pot PvP profile kicks in, screenshots each
 * step and quits.
 */
public final class DevTrainerTest {
    private final Minecraft mc = Minecraft.getMinecraft();
    private int ticks;
    private int phaseTicks;
    private String phase = "menu";

    public static boolean active() {
        return Boolean.getBoolean("novomc.trainers");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        phaseTicks++;
        if (mc.thePlayer != null) {
            mc.gameSettings.pauseOnLostFocus = false;
            mc.guiAchievement.clearAchievements();
        }
        switch (phase) {
            case "menu":
                if (mc.currentScreen instanceof GuiMainMenu && phaseTicks > 40) {
                    mc.displayGuiScreen(new GuiTrainers(mc.currentScreen));
                    next("list");
                }
                break;
            case "list":
                if (phaseTicks == 20) {
                    shot("trainers-list");
                    mc.displayGuiScreen(new GuiClickTest(new GuiMainMenu()));
                    next("cps");
                }
                break;
            case "cps":
                if (phaseTicks == 10) {
                    shot("trainer-cps");
                    Trainers.INSTANCE.start("aim");
                    next("aim");
                }
                break;
            case "aim":
                if (mc.thePlayer == null || !Trainers.INSTANCE.inTrainerWorld()) {
                    phaseTicks = 0;
                    break;
                }
                attackTarget();
                if (phaseTicks == 200) {
                    shot("trainer-aim");
                }
                if (phaseTicks == 60 + 600 + 20) {
                    shot("trainer-aim-results");
                    System.out.println("[NovoMC] Trainer aim best: " + TrainerScores.best("aim"));
                    Trainers.INSTANCE.start("bridge");
                    next("bridge");
                }
                break;
            case "bridge":
                if (phaseTicks == 60) {
                    shot("trainer-bridge");
                    Trainers.INSTANCE.start("pots");
                    next("pots");
                }
                break;
            case "pots":
                if (phaseTicks == 40) {
                    mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
                } else if (phaseTicks == 60) {
                    shot("trainer-pots");
                } else if (phaseTicks >= 62 && phaseTicks <= 69) {
                    // Shift-click one potion per tick from the inventory into the hotbar.
                    int slot = 9 + (phaseTicks - 62);
                    mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, 0, 1, mc.thePlayer);
                } else if (phaseTicks == 80) {
                    mc.displayGuiScreen(null);
                } else if (phaseTicks == 120) {
                    shot("trainer-pots-results");
                    System.out.println("[NovoMC] Trainer pots best: " + TrainerScores.best("pots"));
                    System.out.println("[NovoMC] Auto profile mode: " + profileMode());
                    System.out.println("[NovoMC] Autotest finished");
                    mc.shutdown();
                }
                break;
            default:
                break;
        }
    }

    /** Hits the target whenever it's in reach, about five times a second. */
    private void attackTarget() {
        if (phaseTicks % 4 != 0 || mc.theWorld == null) {
            return;
        }
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (e instanceof EntityZombie && mc.thePlayer.getDistanceToEntity(e) < 4.5) {
                mc.thePlayer.swingItem();
                mc.playerController.attackEntity(mc.thePlayer, e);
                return;
            }
        }
    }

    private static String profileMode() {
        for (Module m : NovoClient.modules()) {
            if (m instanceof AutoProfilesModule) {
                return String.valueOf(((AutoProfilesModule) m).currentMode());
            }
        }
        return "none";
    }

    private void next(String p) {
        phase = p;
        phaseTicks = 0;
    }

    private void shot(String name) {
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        System.out.println("[NovoMC] Trainer test screenshot " + name);
    }
}
