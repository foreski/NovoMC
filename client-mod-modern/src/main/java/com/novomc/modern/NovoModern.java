package com.novomc.modern;

import com.novomc.client.config.Catalog;
import com.novomc.client.config.ModConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import com.novomc.modern.ui.NovoHudEditor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Novo Client for modern Minecraft (Fabric, 26.3). Reads the same catalog and
 * {@code config/novomc/modules.json} as the 1.8.9 client, so the launcher's
 * Mod Library drives both, and HUD layouts use the same coordinates.
 */
public class NovoModern implements ClientModInitializer {
    private static final List<Module> MODULES = new ArrayList<Module>();
    private static final KeyMapping HUD_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.novomc.hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, Tweaks.CATEGORY));
    private static ModConfig config;
    private static AutoProfiles profiles;
    private int ticks;

    public static ModConfig config() {
        return config;
    }

    public static File gameDir() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    public static List<Module> modules() {
        return Collections.unmodifiableList(MODULES);
    }

    @Override
    public void onInitializeClient() {
        Catalog.load();
        config = new ModConfig(gameDir());
        config.load();

        profiles = new AutoProfiles();
        Collections.addAll(MODULES,
                new Huds.Fps(), new Huds.Cps(), new Huds.Keystrokes(), new Huds.Coordinates(), new Huds.ArmorStatus(),
                new Huds.PotionEffects(), new Huds.Ping(), new Huds.Clock(), new Huds.Memory(),
                new PvpHuds.Direction(), new PvpHuds.Saturation(), new PvpHuds.ItemCounter(), new PvpHuds.ComboCounter(),
                new PvpHuds.ReachDisplay(), new PvpHuds.ServerAddress(), new PvpHuds.Playtime(), new PvpHuds.DayCounter(),
                new Tweaks.ToggleSprint(), new Tweaks.Zoom(), new Tweaks.Fullbright(), 
                new Visuals.Crosshair(), new Visuals.HitColor(), new Visuals.BlockOutline(), new Visuals.Freelook(), 
                new Extras.HurtCam(), new Extras.FovChanger(), new Extras.WeatherChanger(), new Extras.UnfocusedFps(), new Extras.Stopwatch(),
                new Extras.PackDisplay(), new Extras.LowHealth(), new Extras.Cooldowns(), new Extras.ModList(), 
                new More.Scoreboard(), new More.Chat(), new More.Nametags(), new More.Hitbox(), new More.ChunkBorders(), new More.Waypoints(), 
                new Pvp.AttackCooldown(), new Pvp.TotemPops(), new Pvp.TargetHud(), new Pvp.ArmorAlert(), new Pvp.MaceHud(), new Pvp.HitSounds(),
                new Pvp.ParticleChanger(), new Pvp.TntCountdown(), new Pvp.TabEditor(), profiles,
                new Menus.CustomMenus(), new Menus.MainMenu(), new Menus.LoadingScreen(), new Cosmetics.NovoCapes());
        // Mods switch on once the game is up: options and the player don't exist yet here.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> apply());

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("novomc", "hud"), NovoModern::renderHud);
        PvpHuds.register();
        Pvp.register();
        com.novomc.modern.trainer.Trainers.register();
        DevCheck.register();
        System.out.println("[NovoMC] Novo Client (modern) loaded " + MODULES.size() + " mods");
    }

    /** Brings every module in line with the config. */
    public static void apply() {
        for (Module m : MODULES) {
            m.setEnabled(config.enabled(m.def));
        }
    }

    private void tick(Minecraft mc) {
        // Pick up edits from the launcher's Mod Library about once a second.
        if (++ticks % 20 == 0 && config.reloadIfChanged()) {
            apply();
        }
        while (HUD_KEY.consumeClick()) {
            if (mc.gui.screen() == null && mc.level != null) {
                mc.gui.setScreen(new NovoHudEditor(null));
            }
        }
        for (Module m : MODULES) {
            if (m.isEnabled()) {
                m.tick();
            }
        }
        Cosmetics.tickPresence(mc);
    }

    public static List<HudModule> hudModules() {
        List<HudModule> out = new ArrayList<HudModule>();
        for (Module m : MODULES) {
            if (m instanceof HudModule h) {
                out.add(h);
            }
        }
        return out;
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Extras.LowHealth.render(g);
        // Not called while the HUD is hidden (F1).
        for (HudModule h : hudModules()) {
            if (h.isEnabled() && h.visible()) {
                int[] b = bounds(h, g.guiWidth(), g.guiHeight());
                g.pose().pushMatrix();
                g.pose().translate(b[0], b[1]);
                g.pose().scale(h.scale(), h.scale());
                h.render(g);
                g.pose().popMatrix();
            }
        }
        if (profiles.isEnabled()) {
            profiles.renderToast(g);
        }
        com.novomc.modern.trainer.Trainers.render(g);
    }

    /** Same placement as 1.8.9: screen fractions, clamped on screen; default columns stack. */
    public static int[] bounds(HudModule h, int sw, int sh) {
        float s = h.scale();
        int w = Math.round(h.width() * s);
        int ht = Math.round(h.height() * s);
        int x = Math.round(h.x() * sw - (h.def.centered ? w / 2f : 0));
        x = Math.max(0, Math.min(sw - w, x));
        int y = h.stacked() ? stackTop(h, sh) : Math.round(h.y() * sh);
        y = Math.max(0, Math.min(sh - ht, y));
        return new int[] {x, y, w, ht};
    }

    private static int stackTop(HudModule target, int screenHeight) {
        int y = Math.round(target.def.defaultY * screenHeight);
        for (HudModule m : hudModules()) {
            if (m == target) {
                break;
            }
            if (m.isEnabled() && m.visible() && m.stacked() && target.def.stack.equals(m.def.stack)) {
                y += Math.round(m.height() * m.scale()) + 2;
            }
        }
        return y;
    }
}
