package com.novomc.client;

import com.google.gson.JsonPrimitive;
import com.novomc.client.module.Module;
import com.novomc.client.modules.AutoGgModule;
import com.novomc.client.modules.EntityCullingModule;
import com.novomc.client.modules.HitSoundsModule;
import com.novomc.client.modules.LowHealthModule;
import com.novomc.client.modules.WaypointsModule;
import com.novomc.client.modules.FreelookModule;
import com.novomc.client.modules.HurtCamModule;
import com.novomc.client.modules.NickHiderModule;
import com.novomc.client.util.NovoFont;
import com.novomc.client.gui.GuiHudEditor;
import com.novomc.client.module.HudModule;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/**
 * Development check of the mods that change behavior, only active with
 * {@code -Dnovomc.modcheck=true}. In a flat world with colored walls around
 * the player, it switches each mod on alone, drives it the way the player
 * would (key bindings, mouse turns, effects), asserts what should happen and
 * screenshots the visual ones. Prints one CHECK PASS/FAIL line per assertion.
 */
public final class DevModuleCheck {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<Object[]> steps = new ArrayList<Object[]>();
    private int menuTicks;
    private boolean started;
    private int ticks;
    private int wait;
    private int step;
    private int passed;
    private int failed;
    private Robot robot;

    /** Dev (named) or production (SRG) method name. */
    private static String name(String named, String srg) {
        for (Method m : GuiScreen.class.getDeclaredMethods()) {
            if (m.getName().equals(named)) {
                return named;
            }
        }
        return srg;
    }

    public static boolean active() {
        return Boolean.getBoolean("novomc.modcheck");
    }

    public DevModuleCheck() {
        script();
    }

    /** Runs `action` `delay` ticks after the previous step. */
    private void then(int delay, Runnable action) {
        steps.add(new Object[] {delay, action});
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        System.out.println("[NovoMC] CHECK " + (ok ? "PASS" : "FAIL") + " " + name + " - " + detail);
    }

    private static <T extends Module> T find(Class<T> type) {
        for (Module m : NovoClient.modules()) {
            if (type.isInstance(m)) {
                return type.cast(m);
            }
        }
        throw new IllegalStateException(type.getSimpleName() + " not registered");
    }

    private void only(String... ids) {
        for (Module m : NovoClient.modules()) {
            boolean on = false;
            for (String id : ids) {
                on |= id.equals(m.id());
            }
            NovoClient.config().setEnabled(m.id(), on);
        }
        NovoClient.apply();
    }

    private void set(String id, String key, Object value) {
        NovoClient.config().setSetting(id, key,
                value instanceof Boolean ? new JsonPrimitive((Boolean) value)
                        : value instanceof Number ? new JsonPrimitive((Number) value) : new JsonPrimitive(String.valueOf(value)));
        NovoClient.apply();
    }

    private void key(KeyBinding binding, boolean down) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), down);
        if (down) {
            KeyBinding.onTick(binding.getKeyCode());
        }
    }

    private void look(float yaw, float pitch) {
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
    }

    private void cmd(String c) {
        mc.thePlayer.sendChatMessage(c);
    }

    private void shot(String name) {
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, "check-" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private void script() {
        // Scene: a red wall south, a blue wall west, a yellow pillar north, and an armor stand.
        then(20, new Runnable() {
            public void run() {
                only();
                for (String c : new String[] {
                    "/gamerule doDaylightCycle false", "/gamerule doMobSpawning false", "/gamerule sendCommandFeedback false",
                    "/time set 6000", "/weather clear", "/tp @p ~ ~ ~ 0 0",
                    "/fill ~-3 ~ ~5 ~3 ~4 ~5 wool 14", "/fill ~-6 ~ ~-3 ~-6 ~4 ~3 wool 11", "/fill ~ ~ ~-6 ~ ~5 ~-6 wool 4",
                    "/summon ArmorStand ~1 ~ ~3"}) {
                    cmd(c);
                }
            }
        });
        then(30, new Runnable() {
            public void run() {
                look(0, 10);
                shot("baseline");
            }
        });

        // ---- Freelook: hold the key, turn the mouse; the camera turns, the player doesn't.
        then(5, new Runnable() {
            public void run() {
                only("freelook");
                look(0, 10);
                key(NovoClient.FREELOOK_KEY, true);
            }
        });
        then(3, new Runnable() {
            public void run() {
                check("freelook.activates", FreelookModule.active(), "holding the freelook key starts freelook");
                check("freelook.thirdPerson", mc.gameSettings.thirdPersonView == 1, "view=" + mc.gameSettings.thirdPersonView);
                // What EntityRenderer does with mouse movement: 600 units = 90 degrees.
                mc.thePlayer.setAngles(600f, 0f);
                check("freelook.playerStill", Math.abs(mc.thePlayer.rotationYaw) < 0.01f, "player yaw=" + mc.thePlayer.rotationYaw);
                float cam = FreelookModule.yaw(mc.thePlayer, mc.thePlayer.rotationYaw);
                check("freelook.cameraTurns", Math.abs(cam - 90f) < 0.01f, "camera yaw=" + cam);
            }
        });
        then(4, new Runnable() {
            public void run() {
                shot("freelook");
                key(NovoClient.FREELOOK_KEY, false);
            }
        });
        then(3, new Runnable() {
            public void run() {
                check("freelook.releases", !FreelookModule.active(), "releasing the key ends freelook");
                check("freelook.viewRestored", mc.gameSettings.thirdPersonView == 0, "view=" + mc.gameSettings.thirdPersonView);
                look(0, 10);
            }
        });

        // ---- Freelook with a real key press (OS input, only when the game window has focus).
        then(2, new Runnable() {
            public void run() {
                only("freelook");
                look(0, 10);
                if (!Display.isActive()) {
                    System.out.println("[NovoMC] CHECK SKIP freelook.realKey - game window not focused");
                    return;
                }
                try {
                    robot = new Robot();
                    robot.keyPress(KeyEvent.VK_ALT);
                } catch (Exception e) {
                    System.out.println("[NovoMC] CHECK SKIP freelook.realKey - " + e);
                }
            }
        });
        then(5, new Runnable() {
            public void run() {
                if (robot != null) {
                    check("freelook.realKey", FreelookModule.active(), "real Left Alt press -> binding down=" + NovoClient.FREELOOK_KEY.isKeyDown()
                            + ", keyCode=" + NovoClient.FREELOOK_KEY.getKeyCode() + ", Keyboard.isKeyDown(LMENU)=" + Keyboard.isKeyDown(Keyboard.KEY_LMENU));
                    robot.keyRelease(KeyEvent.VK_ALT);
                }
            }
        });
        then(3, new Runnable() {
            public void run() {
                if (robot != null) {
                    check("freelook.realKeyRelease", !FreelookModule.active(), "releasing Alt ends freelook");
                }
            }
        });

        // ---- HUD editor: drag a module with the mouse and check it moved.
        then(2, new Runnable() {
            public void run() {
                only("fps");
                mc.displayGuiScreen(new GuiHudEditor());
            }
        });
        then(3, new Runnable() {
            public void run() {
                HudModule fps = null;
                for (HudModule h : NovoClient.hudModules()) {
                    if ("fps".equals(h.id())) {
                        fps = h;
                    }
                }
                ScaledResolution sr = new ScaledResolution(mc);
                int[] b = NovoClient.bounds(fps, sr, true);
                int x = b[0] + b[2] / 2;
                int y = b[1] + b[3] / 2;
                float beforeX = fps.x();
                try {
                    GuiScreen s = mc.currentScreen;
                    Method click = GuiScreen.class.getDeclaredMethod(name("mouseClicked", "func_73864_a"), int.class, int.class, int.class);
                    Method move = GuiScreen.class.getDeclaredMethod(name("mouseClickMove", "func_146273_a"), int.class, int.class, int.class, long.class);
                    Method release = GuiScreen.class.getDeclaredMethod(name("mouseReleased", "func_146286_b"), int.class, int.class, int.class);
                    click.setAccessible(true);
                    move.setAccessible(true);
                    release.setAccessible(true);
                    click.invoke(s, x, y, 0);
                    move.invoke(s, x + 80, y + 40, 0, 100L);
                    release.invoke(s, x + 80, y + 40, 0);
                    check("hud_editor.drag", Math.abs(fps.x() - beforeX) > 0.01f, "fps x " + beforeX + " -> " + fps.x());
                } catch (Exception e) {
                    check("hud_editor.drag", false, e.toString());
                }
                shot("hud-editor");
                fps.resetPosition();
                mc.displayGuiScreen(null);
            }
        });

        // ---- Snaplook (unbound by default): bind V, hold it.
        then(2, new Runnable() {
            public void run() {
                only("snaplook");
                NovoClient.SNAPLOOK_KEY.setKeyCode(Keyboard.KEY_V);
                KeyBinding.resetKeyBindingArrayAndHash();
                key(NovoClient.SNAPLOOK_KEY, true);
            }
        });
        then(3, new Runnable() {
            public void run() {
                check("snaplook.frontView", mc.gameSettings.thirdPersonView == 2, "view=" + mc.gameSettings.thirdPersonView);
                key(NovoClient.SNAPLOOK_KEY, false);
            }
        });
        then(3, new Runnable() {
            public void run() {
                check("snaplook.restores", mc.gameSettings.thirdPersonView == 0, "view=" + mc.gameSettings.thirdPersonView);
                NovoClient.SNAPLOOK_KEY.setKeyCode(Keyboard.KEY_NONE);
                KeyBinding.resetKeyBindingArrayAndHash();
            }
        });

        // ---- Zoom: hold C.
        then(2, new Runnable() {
            public void run() {
                only("zoom");
                look(0, 10);
                key(NovoClient.ZOOM_KEY, true);
            }
        });
        then(15, new Runnable() {
            public void run() {
                shot("zoom");
                key(NovoClient.ZOOM_KEY, false);
            }
        });

        // ---- Toggle Sprint: walk forward, tap sprint once, keep sprinting without holding it.
        then(10, new Runnable() {
            public void run() {
                only("toggle_sprint");
                cmd("/tp @p ~ ~ ~-20 180 0");
            }
        });
        then(5, new Runnable() {
            public void run() {
                key(mc.gameSettings.keyBindForward, true);
                key(mc.gameSettings.keyBindSprint, true);
            }
        });
        then(1, new Runnable() {
            public void run() {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            }
        });
        then(15, new Runnable() {
            public void run() {
                check("toggle_sprint.sprints", mc.thePlayer.isSprinting(), "sprinting after one tap");
                key(mc.gameSettings.keyBindForward, false);
                only();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                cmd("/tp @p ~ ~ ~20 0 10");
            }
        });

        // ---- Fullbright, Time Changer, Weather Changer, FOV Changer, Hurt Cam.
        then(5, new Runnable() {
            public void run() {
                only("fullbright");
                check("fullbright.gamma", mc.gameSettings.gammaSetting >= 99f, "gamma=" + mc.gameSettings.gammaSetting);
                only();
                check("fullbright.restores", mc.gameSettings.gammaSetting <= 1f, "gamma=" + mc.gameSettings.gammaSetting);
                set("time_changer", "time", 18000);
                only("time_changer");
            }
        });
        then(10, new Runnable() {
            public void run() {
                long t = mc.theWorld.getWorldTime() % 24000;
                check("time_changer.time", Math.abs(t - 18000) < 40, "world time=" + t);
                look(0, -20);
                shot("time-night");
                set("weather_changer", "weather", "rain");
                only("weather_changer");
            }
        });
        then(10, new Runnable() {
            public void run() {
                float rain = mc.theWorld.getRainStrength(1f);
                check("weather_changer.rain", rain > 0.9f, "rain strength=" + rain);
                shot("weather-rain");
                only("fov_changer");
                cmd("/effect @p 1 30 3");
            }
        });
        then(10, new Runnable() {
            public void run() {
                float fov = mc.thePlayer.getFovModifier();
                check("fov_changer.static", Math.abs(fov - 1f) < 0.001f, "FOV modifier with Speed IV=" + fov);
                cmd("/effect @p clear");
                only("hurt_cam");
                check("hurt_cam.scale", Math.abs(HurtCamModule.scale(14f)) < 0.001f, "tilt at intensity 0=" + HurtCamModule.scale(14f));
                look(0, 10);
            }
        });

        // ---- Motion blur and menu blur (post shaders).
        then(3, new Runnable() {
            public void run() {
                only("motion_blur");
            }
        });
        then(5, new Runnable() {
            public void run() {
                String name = mc.entityRenderer.getShaderGroup() != null ? mc.entityRenderer.getShaderGroup().getShaderGroupName() : "none";
                check("motion_blur.shader", name.contains("motion_blur"), "shader=" + name);
                only("menu_blur");
            }
        });
        then(3, new Runnable() {
            public void run() {
                mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            }
        });
        then(5, new Runnable() {
            public void run() {
                String name = mc.entityRenderer.getShaderGroup() != null ? mc.entityRenderer.getShaderGroup().getShaderGroupName() : "none";
                check("menu_blur.shader", name.contains("blur"), "shader=" + name);
                mc.displayGuiScreen(null);
            }
        });

        // ---- Entity culling and Nick Hider (logic).
        then(3, new Runnable() {
            public void run() {
                only("entity_culling");
                EntityArmorStand far = new EntityArmorStand(mc.theWorld, mc.thePlayer.posX + 100, mc.thePlayer.posY, mc.thePlayer.posZ);
                Entity view = mc.getRenderViewEntity();
                check("entity_culling.far", EntityCullingModule.cull(far, view.posX, view.posY, view.posZ), "armor stand 100 blocks away culled");
                EntityArmorStand near = new EntityArmorStand(mc.theWorld, mc.thePlayer.posX + 3, mc.thePlayer.posY, mc.thePlayer.posZ);
                check("entity_culling.near", !EntityCullingModule.cull(near, view.posX, view.posY, view.posZ), "armor stand 3 blocks away drawn");
                set("nick_hider", "nick", "Hidden");
                only("nick_hider");
                String name = mc.getSession().getUsername();
                String out = NickHiderModule.replace("<" + name + "> hi");
                check("nick_hider.replace", out.equals("<Hidden> hi"), "\"" + out + "\"");
                // The modern font draws HUD text itself, so it must hide the name too.
                check("nick_hider.modernFont", NovoFont.REGULAR.width(name + ": 99") == NovoFont.REGULAR.width("Hidden: 99"),
                        "sidebar line measured with the nick");
                // Hypixel hides zero-width characters in sidebar text; they must not show as "?".
                NovoFont f = NovoFont.REGULAR;
                float plain = f.width("Winstreak");
                check("font.invisibleChars", Math.abs(f.width("Win​strea‌k") - plain) < 0.01f, "width with zero-width chars=" + f.width("Win​strea‌k") + " vs " + plain);
                check("font.symbolFallback", f.width("⚔") > 0, "crossed swords drawn with the vanilla font, width=" + f.width("⚔"));
            }
        });

        // ---- Batch 4: Auto GG, Auto Text, Hit Sounds, Low Health, Waypoints, Screenshots.
        then(3, new Runnable() {
            public void run() {
                only("auto_gg");
                check("auto_gg.triggers", AutoGgModule.endOfGame("                 Winner: Steve") && !AutoGgModule.endOfGame("hello there"),
                        "end-of-game lines detected, normal chat ignored");
                MinecraftForge.EVENT_BUS.post(new ClientChatReceivedEvent((byte) 0, new ChatComponentText("               Winner: Steve")));
                check("auto_gg.queues", find(AutoGgModule.class).pending(), "gg queued after the win message");
            }
        });
        then(30, new Runnable() {
            public void run() {
                check("auto_gg.sends", !find(AutoGgModule.class).pending(), "gg sent after the delay");
                set("auto_text", "message1", "/time set 1000");
                only("auto_text");
                NovoClient.AUTO_TEXT_KEYS[0].setKeyCode(Keyboard.KEY_N);
                KeyBinding.resetKeyBindingArrayAndHash();
                key(NovoClient.AUTO_TEXT_KEYS[0], true);
                KeyBinding.setKeyBindState(Keyboard.KEY_N, false);
            }
        });
        then(20, new Runnable() {
            public void run() {
                long t = mc.theWorld.getWorldTime() % 24000;
                check("auto_text.sends", Math.abs(t - 1000) < 40, "Auto Text 1 ran /time set 1000, time=" + t + ", sent=" + find(com.novomc.client.modules.AutoTextModule.class).sent
                        + ", enabled=" + find(com.novomc.client.modules.AutoTextModule.class).isEnabled() + ", key=" + NovoClient.AUTO_TEXT_KEYS[0].getKeyCode());
                NovoClient.AUTO_TEXT_KEYS[0].setKeyCode(Keyboard.KEY_NONE);
                KeyBinding.resetKeyBindingArrayAndHash();
                cmd("/time set 6000");
                only("hit_sounds");
                cmd("/summon Pig ~ ~ ~2 {NoAI:1}");
            }
        });
        then(10, new Runnable() {
            public void run() {
                HitSoundsModule hs = find(HitSoundsModule.class);
                int before = hs.played;
                for (Entity e : mc.theWorld.loadedEntityList) {
                    if (e instanceof net.minecraft.entity.passive.EntityPig) {
                        mc.playerController.attackEntity(mc.thePlayer, e);
                        break;
                    }
                }
                check("hit_sounds.plays", hs.played > before, "sound played on hit");
                only("low_health");
                mc.thePlayer.setHealth(3f);
            }
        });
        then(3, new Runnable() {
            public void run() {
                check("low_health.shows", find(LowHealthModule.class).showing, "warning at 1.5 hearts");
                shot("low-health");
                cmd("/effect @p 6 1 10");
                only("waypoints");
            }
        });
        then(5, new Runnable() {
            public void run() {
                WaypointsModule wm = find(WaypointsModule.class);
                int before = wm.here().size();
                WaypointsModule.Waypoint w = wm.addHere();
                w.z += 12;
                wm.save();
                java.io.File file = new java.io.File(mc.mcDataDir, "config/novomc/waypoints.json");
                check("waypoints.add", wm.here().size() == before + 1 && file.isFile(), "added and saved to " + file.getName());
                look(0, 0);
            }
        });
        then(5, new Runnable() {
            public void run() {
                shot("waypoint");
                WaypointsModule wm = find(WaypointsModule.class);
                wm.here().remove(wm.here().size() - 1);
                wm.save();
                only("screenshots");
                net.minecraft.util.IChatComponent msg = ScreenShotHelper.saveScreenshot(mc.mcDataDir, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                String text = msg.getUnformattedText();
                check("screenshots.actions", text.contains("[Open]") && text.contains("[Copy]") && text.contains("[Delete]"), text);
            }
        });

        // ---- Mod List HUD (ArrayList style), in both color modes.
        then(3, new Runnable() {
            public void run() {
                only("module_list", "freelook", "toggle_sprint", "zoom", "hit_color", "waypoints", "auto_gg", "modern_font");
                // Hide the new-world "Press E" tip so the screenshots show the list itself.
                mc.guiAchievement.clearAchievements();
                set("module_list", "colors", "rainbow");
                com.novomc.client.modules.ModListModule ml = find(com.novomc.client.modules.ModListModule.class);
                // Toggle Sprint is itself a HUD element, so "Hide HUD mods" leaves it out: watermark + 5 lines.
                // Toggle Sprint and Modern Font stay out ("Hide HUD mods" + always-on plumbing): watermark + 5 lines.
                check("module_list.lists", ml.visible() && ml.height(false) == 6 * 10, "height=" + ml.height(false) + " (watermark + 5 mods)");
            }
        });
        then(5, new Runnable() {
            public void run() {
                shot("mod-list-rainbow");
                set("module_list", "colors", "novomc");
            }
        });
        then(5, new Runnable() {
            public void run() {
                shot("mod-list-novomc");
            }
        });

        // ---- Visual mods: one screenshot each.
        for (final String id : new String[] {"chunk_borders", "hitbox", "block_outline", "crosshair", "fog", "old_animations", "hit_color"}) {
            then(3, new Runnable() {
                public void run() {
                    only(id);
                    look(0, 25);
                }
            });
            then(6, new Runnable() {
                public void run() {
                    shot(id);
                }
            });
        }

        then(5, new Runnable() {
            public void run() {
                // Back to the catalog defaults for whoever uses this instance next.
                for (Module m : NovoClient.modules()) {
                    NovoClient.config().setEnabled(m.id(), m.def.enabledByDefault);
                }
                NovoClient.apply();
                NovoClient.save();
                System.out.println("[NovoMC] Mod check: " + passed + " passed, " + failed + " failed");
                System.out.println("[NovoMC] Autotest finished");
                mc.shutdown();
            }
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!started) {
            if (mc.currentScreen instanceof GuiMainMenu && ++menuTicks > 40) {
                started = true;
                WorldSettings ws = new WorldSettings(7L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT);
                ws.enableCommands();
                mc.gameSettings.pauseOnLostFocus = false;
                mc.launchIntegratedServer("novomc-check-" + System.currentTimeMillis(), "NovoMC Mod Check", ws);
            }
            return;
        }
        if (mc.thePlayer == null || step >= steps.size()) {
            return;
        }
        ticks++;
        if (ticks < 40) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Object[] s = steps.get(step++);
        ((Runnable) s[1]).run();
        wait = step < steps.size() ? (Integer) steps.get(step)[0] : 0;
    }
}
