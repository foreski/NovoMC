package com.novomc.modern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development check, only active with {@code -Dnovomc.autotest=true}: screenshots
 * the Novo menus (loading screen during a resource reload, title screen,
 * Options), creates a fresh creative world, screenshots the HUD and the pause
 * menu, gives a mace to check that Auto Mode Profiles switches to the Mace
 * profile, and quits.
 */
public final class DevCheck {
    private int ticks;
    private int menuTicks;
    private boolean created;

    public static void register() {
        if (Boolean.getBoolean("novomc.autotest")) {
            DevCheck check = new DevCheck();
            ClientTickEvents.END_CLIENT_TICK.register(check::tick);
        }
    }

    private void tick(Minecraft mc) {
        if (mc.player == null) {
            menuTicks++;
            if (menuTicks == 40) {
                mc.reloadResourcePacks();
            } else if (menuTicks == 44) {
                shot(mc, "loading");
            } else if (menuTicks == 140) {
                // Past the first-launch accessibility screen, to the (Novo) title screen.
                mc.gui.setScreen(new TitleScreen());
            } else if (menuTicks == 160) {
                shot(mc, "title");
                mc.gui.setScreen(new OptionsScreen(mc.gui.screen(), mc.options));
            } else if (menuTicks == 175) {
                shot(mc, "options");
                mc.gui.setScreen(new com.novomc.modern.ui.NovoMods(new TitleScreen()));
            } else if (menuTicks == 185) {
                shot(mc, "mods");
                mc.gui.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(new TitleScreen(), mc, mc.options));
            } else if (menuTicks == 195) {
                shot(mc, "video");
            }
            if (!created && menuTicks == 205) {
                created = true;
                LevelSettings settings = new LevelSettings("Novo Modern Test", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("novo-modern-test-" + System.currentTimeMillis(), settings,
                        new WorldOptions(1234L, false, false), WorldPresets::createNormalWorldDimensions, null);
            }
            return;
        }
        ticks++;
        if (ticks == 50) {
            for (String id : new String[] {"direction", "saturation", "item_counter", "day_counter", "playtime", "crosshair", "hit_color", "block_outline", "module_list", "pack_display", "cooldowns", "weather_changer", "fov_changer", "hurt_cam", "scoreboard", "chat", "nametags", "hitbox", "chunk_borders", "waypoints", "attack_cooldown", "totem_pops", "target_hud", "armor_alert", "mace_hud", "hit_sounds", "particle_changer", "tnt_countdown", "tab_editor"}) {
                NovoModern.config().setEnabled(id, true);
            }
            NovoModern.config().setSetting("crosshair", "color", new com.google.gson.JsonPrimitive("#22D3EE"));
            NovoModern.config().setSetting("block_outline", "fill", new com.google.gson.JsonPrimitive(true));
            NovoModern.config().setSetting("weather_changer", "weather", new com.google.gson.JsonPrimitive("rain"));
            mc.player.connection.sendCommand("give @s minecraft:ender_pearl 4");
            NovoModern.config().setSetting("nametags", "showOwn", new com.google.gson.JsonPrimitive(true));
            for (String c : new String[] {"scoreboard objectives add novo dummy \"Novo Test\"", "scoreboard objectives setdisplay sidebar novo",
                    "scoreboard players set NovoTester novo 5", "scoreboard players set Kills novo 12", "summon minecraft:pig ~2 ~ ~4 {NoAI:1b}",
                    "summon minecraft:tnt ~-3 ~ ~6 {fuse:300}", "item replace entity @s armor.chest with minecraft:diamond_chestplate[damage=500]",
                    "give @s minecraft:wind_charge 12", "give @s minecraft:enchanted_golden_apple 2",
                    "give @s minecraft:splash_potion[potion_contents={potion:\"minecraft:strong_healing\"}] 5"}) {
                mc.player.connection.sendCommand(c);
            }
            More.Waypoints.Waypoint w = More.Waypoints.get().addHere();
            w.x += 6;
            w.z += 10;
            w.name = "Base";
            More.Waypoints.get().save();
            NovoModern.config().setSetting("hit_color", "color", new com.google.gson.JsonPrimitive("#22D3EE"));
            NovoModern.config().setSetting("hit_color", "opacity", new com.google.gson.JsonPrimitive(60));
            NovoModern.apply();
            for (int i = 0; i < 3; i++) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Stacked message"));
            }
            mc.player.connection.sendCommand("give @s totem_of_undying 3");
            mc.player.connection.sendCommand("give @s end_crystal 32");
        }
        if (ticks == 92) {
            // A hit on the pig (Target HUD, Hit Sounds, particles) and a totem pop of our own.
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.animal.pig.Pig) {
                    mc.gameMode.attack(mc.player, e);
                    break;
                }
            }
            mc.player.handleEntityEvent(net.minecraft.world.entity.EntityEvent.PROTECTED_FROM_DEATH);
        }
        if (ticks == 95) {
            mc.player.getCooldowns().addCooldown(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), 400);
        }
        if (ticks == 60) {
            mc.gui.setScreen(new PauseScreen(true));
        } else if (ticks == 75) {
            shot(mc, "pause");
            mc.gui.setScreen(null);
        } else if (ticks == 100) {
            shot(mc, "hud");
            mc.player.connection.sendCommand("gamemode creative");
            mc.player.connection.sendCommand("give @s mace");
            mc.player.connection.sendCommand("give @s netherite_sword");
        } else if (ticks == 120) {
            Visuals.Freelook.KEY.setDown(true);
        } else if (ticks == 135) {
            shot(mc, "freelook");
            Visuals.Freelook.KEY.setDown(false);
        } else if (ticks == 140) {
            mc.player.connection.sendCommand("tp @s ~ ~30 ~-6 0 25");
        } else if (ticks == 160) {
            mc.gui.hud.getChat().clearMessages(false);
        } else if (ticks == 165) {
            shot(mc, "waypoint");
        } else if (ticks == 175) {
            shot(mc, "profile");
        } else if (ticks == 190) {
            mc.gui.setScreen(new com.novomc.modern.ui.NovoHudEditor(null));
        } else if (ticks == 205) {
            shot(mc, "hudeditor");
            mc.gui.setScreen(new com.novomc.modern.ui.NovoMods(null));
        } else if (ticks == 215) {
            shot(mc, "mods-ingame");
            mc.gui.setScreen(null);
        } else if (ticks == 230) {
            for (Module m : NovoModern.modules()) {
                if (m instanceof AutoProfiles p) {
                    System.out.println("[NovoMC] Modern profile mode: " + p.currentMode());
                }
            }
            mc.gui.setScreen(new com.novomc.modern.ui.NovoTrainers(null));
        } else if (ticks == 245) {
            shot(mc, "trainers");
            // Into the trainer world (a new world, so ticks pause while it loads).
            com.novomc.modern.trainer.Trainers.start("mace_dive");
        } else if (ticks == 300) {
            System.out.println("[NovoMC] In trainer world: " + com.novomc.modern.trainer.Trainers.inTrainerWorld());
            shot(mc, "trainer-mace");
            com.novomc.modern.trainer.Trainers.start("crystal_speed");
        } else if (ticks == 330) {
            shot(mc, "trainer-crystal");
            com.novomc.modern.trainer.Trainers.start("spear_charge");
        } else if (ticks == 395) {
            mc.player.connection.sendCommand("damage @e[type=minecraft:husk,limit=1,sort=nearest] 1");
        } else if (ticks == 397) {
            shot(mc, "hitcolor");
        } else if (ticks == 400) {
            shot(mc, "trainer-spear");
            System.out.println("[NovoMC] Autotest finished");
            mc.stop();
        }
    }

    private static void shot(Minecraft mc, String name) {
        // Saved under a timestamp; the log line says which screen it was.
        Screenshot.grab(mc, false);
        System.out.println("[NovoMC] Modern autotest screenshot " + name);
    }
}
