package com.novomc.client;

import com.novomc.client.gui.GuiHudEditor;
import com.novomc.client.gui.GuiModules;
import com.novomc.client.gui.GuiResourcePacks;
import com.novomc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Development smoke test, only active with {@code -Dnovomc.autotest=true}:
 * screenshots the main menu, creates a flat world, turns every module on,
 * sets up gear, mobs and dropped items, attacks once, then screenshots the
 * HUD, an inventory (menu blur), the HUD editor and the mods list, and quits.
 */
public final class DevAutoTest {
    private final Minecraft mc = Minecraft.getMinecraft();
    private int menuTicks;
    private boolean started;
    private int ticks;

    public static boolean active() {
        return Boolean.getBoolean("novomc.autotest");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!started) {
            if (mc.currentScreen instanceof GuiMainMenu && ++menuTicks == 60) {
                shot("novomc-menu");
            } else if (menuTicks > 70) {
                started = true;
                WorldSettings ws = new WorldSettings(42L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT);
                ws.enableCommands();
                mc.launchIntegratedServer("novomc-autotest-" + System.currentTimeMillis(), "NovoMC Autotest", ws);
            }
            return;
        }
        if (mc.thePlayer == null) {
            return;
        }
        ticks++;
        switch (ticks) {
            case 40:
                for (Module m : NovoClient.modules()) {
                    NovoClient.config().setEnabled(m.id(), true);
                }
                // 2D Items and Item Physics both draw dropped items; show physics.
                NovoClient.config().setEnabled("items_2d", false);
                NovoClient.apply();
                for (String cmd : new String[] {
                    "/gamerule doDaylightCycle false", "/gamerule doMobSpawning false", "/weather clear",
                    "/give @p diamond_helmet", "/give @p diamond_chestplate", "/give @p iron_leggings",
                    "/give @p diamond_boots", "/give @p diamond_sword", "/give @p arrow 37",
                    "/give @p golden_apple 5", "/give @p ender_pearl 8",
                    "/effect @p 1 600 1", "/effect @p 12 600 0",
                    "/summon Pig ~1.5 ~ ~2.2 {NoAI:1}",
                    "/summon Zombie ~-2 ~ ~4 {NoAI:1,CustomName:\"Novo\",CustomNameVisible:1}",
                    "/summon Item ~0.5 ~1 ~1.6 {Item:{id:diamond,Count:3}}",
                    "/summon Item ~-0.6 ~1 ~1.8 {Item:{id:golden_apple,Count:1}}",
                    "/summon Item ~0 ~1 ~2.6 {Item:{id:grass,Count:1}}",
                    "/summon PrimedTnt ~3 ~ ~5 {Fuse:600}",
                    "/summon EnderDragon ~ ~25 ~30 {NoAI:1}",
                    "/scoreboard objectives add novo dummy NovoMC",
                    "/scoreboard objectives setdisplay sidebar novo",
                    // Singleplayer only shows the tab list when an objective is in its slot.
                    "/scoreboard objectives setdisplay list novo",
                    "/scoreboard players set Kills novo 12",
                    "/scoreboard players set Wins novo 4",
                    "/title @p subtitle {\"text\":\"Batch 3\"}",
                    "/title @p title {\"text\":\"NovoMC\"}",
                    "/say repeat", "/say repeat", "/say repeat"}) {
                    mc.thePlayer.sendChatMessage(cmd);
                }
                break;
            case 60:
                // Shift-click armor on (hotbar slots are 36-44 in the inventory container).
                for (int i = 0; i < mc.thePlayer.inventory.mainInventory.length; i++) {
                    ItemStack s = mc.thePlayer.inventory.mainInventory[i];
                    if (s != null && s.getItem() instanceof ItemArmor) {
                        mc.playerController.windowClick(0, i < 9 ? i + 36 : i, 0, 1, mc.thePlayer);
                    }
                }
                mc.thePlayer.inventory.currentItem = 0;
                mc.thePlayer.rotationYaw = 0;
                mc.thePlayer.rotationPitch = 35;
                break;
            case 100:
                for (Entity e : mc.theWorld.loadedEntityList) {
                    if (e instanceof EntityPig) {
                        mc.playerController.attackEntity(mc.thePlayer, e);
                        mc.thePlayer.swingItem();
                    }
                }
                break;
            case 130:
                shot("novomc-hud");
                // Hold Tab for the player list screenshot.
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindPlayerList.getKeyCode(), true);
                break;
            case 132:
                shot("novomc-tab");
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindPlayerList.getKeyCode(), false);
                break;
            case 135:
                mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
                break;
            case 160:
                shot("novomc-blur");
                break;
            case 165:
                mc.displayGuiScreen(new GuiHudEditor());
                break;
            case 190:
                shot("novomc-editor");
                break;
            case 195:
                mc.displayGuiScreen(new GuiModules(null).expand("fps"));
                break;
            case 215:
                shot("novomc-mods");
                break;
            case 220:
                mc.displayGuiScreen(new GuiResourcePacks(null));
                break;
            case 280:
                shot("novomc-packs");
                // Install the top pack to check download, enable and resource reload.
                if (mc.currentScreen instanceof GuiResourcePacks) {
                    System.out.println("[NovoMC] Autotest installing a pack: " + ((GuiResourcePacks) mc.currentScreen).installFirst());
                }
                break;
            case 380:
                shot("novomc-packs-installed");
                System.out.println("[NovoMC] Autotest active packs: " + mc.gameSettings.resourcePacks);
                mc.displayGuiScreen(null);
                break;
            case 385:
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiIngameMenu());
                break;
            case 395:
                shot("novomc-pause");
                mc.displayGuiScreen(null);
                break;
            case 400:
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiDownloadTerrain(mc.getNetHandler()));
                break;
            case 406:
                shot("novomc-loading");
                mc.displayGuiScreen(null);
                break;
            case 420:
                System.out.println("[NovoMC] Autotest finished");
                mc.shutdown();
                break;
            default:
                break;
        }
    }

    private void shot(String name) {
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        System.out.println("[NovoMC] Autotest screenshot " + name);
    }
}
