package com.novomc.client.modules;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novomc.client.NovoClient;
import com.novomc.client.config.Catalog;
import com.novomc.client.module.Module;
import com.novomc.client.profiles.ModeDetector;
import com.novomc.client.profiles.ModeDetector.Mode;
import com.novomc.client.util.Draw;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Auto Mode Profiles: Novo works out the mode you're playing (see
 * {@link ModeDetector}) and keeps a separate setup for each: which HUD and
 * PvP mods are on, where the HUD sits, their settings, and your keybinds.
 * Whatever you change while in a mode is remembered for that mode; the first
 * visit to a mode starts from your current setup.
 *
 * <p>Saved in {@code config/novomc/profiles.json}. Performance and rendering
 * mods stay global, since switching them would rebuild chunks mid-game.
 */
public class AutoProfilesModule extends Module {
    /** Detections in a row (one per second) before switching, so a kit change mid-fight doesn't flap. */
    private static final int STABLE_CHECKS = 3;
    private static final Set<String> GLOBAL = new HashSet<String>(Arrays.asList(
            "auto_profiles", "custom_menus", "video_settings", "main_menu", "loading_screen", "novo_badge",
            "connected_textures", "custom_sky", "better_grass", "better_snow"));

    private final File file = new File(mc.mcDataDir, "config/novomc/profiles.json");
    private JsonObject profiles;
    private Mode current;
    private Mode pending;
    private int pendingCount;
    private int ticks;
    private String toast;
    private long toastAt;

    public AutoProfilesModule() {
        super("auto_profiles");
    }

    @Override
    protected void onEnable() {
        load();
        current = null;
    }

    @Override
    protected void onDisable() {
        if (current != null) {
            store(current);
            save();
        }
        current = null;
    }

    @Override
    public void onTick() {
        if (++ticks % 20 != 0) {
            return;
        }
        Mode mode = ModeDetector.detect(mc);
        if (current == null) {
            current = mode;
            return;
        }
        if (mode == current) {
            pending = null;
            return;
        }
        if (mode != pending) {
            pending = mode;
            pendingCount = 1;
            return;
        }
        if (++pendingCount >= STABLE_CHECKS) {
            switchTo(mode);
        }
    }

    public Mode currentMode() {
        return current;
    }

    private void switchTo(Mode mode) {
        store(current);
        boolean known = profiles.has(mode.id);
        if (known) {
            apply(profiles.getAsJsonObject(mode.id));
        }
        current = mode;
        pending = null;
        save();
        if (bool("notify")) {
            toast = mode.label + (known ? " profile" : " profile (new: starts from your current setup)");
            toastAt = System.currentTimeMillis();
        }
    }

    /**
     * Mods that belong to a profile: HUD, PvP visuals and quality of life,
     * minus the global ones. Hand-saved {@link com.novomc.client.profiles.Presets}
     * use the same set, so the two features can't disagree about what a setup is.
     */
    public static List<String> profiled() {
        List<String> ids = new ArrayList<String>();
        for (Catalog.Def def : Catalog.all()) {
            if (!def.category.equals("performance") && !GLOBAL.contains(def.id)) {
                ids.add(def.id);
            }
        }
        return ids;
    }

    private void store(Mode mode) {
        JsonObject profile = new JsonObject();
        profile.add("modules", NovoClient.config().snapshot(profiled()));
        JsonObject keys = new JsonObject();
        for (KeyBinding kb : mc.gameSettings.keyBindings) {
            keys.addProperty(kb.getKeyDescription(), kb.getKeyCode());
        }
        profile.add("keys", keys);
        profiles.add(mode.id, profile);
    }

    private void apply(JsonObject profile) {
        if (profile.has("modules") && profile.get("modules").isJsonObject()) {
            NovoClient.config().restore(profile.getAsJsonObject("modules"));
            NovoClient.save();
            NovoClient.apply();
        }
        if (profile.has("keys") && profile.get("keys").isJsonObject()) {
            JsonObject keys = profile.getAsJsonObject("keys");
            for (KeyBinding kb : mc.gameSettings.keyBindings) {
                JsonElement code = keys.get(kb.getKeyDescription());
                if (code != null && code.isJsonPrimitive()) {
                    kb.setKeyCode(code.getAsInt());
                }
            }
            KeyBinding.resetKeyBindingArrayAndHash();
            mc.gameSettings.saveOptions();
        }
    }

    private void load() {
        profiles = new JsonObject();
        if (!file.isFile()) {
            return;
        }
        try (Reader r = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(r);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("profiles")) {
                profiles = parsed.getAsJsonObject().getAsJsonObject("profiles");
            }
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't read " + file + ": " + e);
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("profiles", profiles);
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't save " + file + ": " + e);
        }
    }

    /** Modes that already have a saved setup, for the Mods screen. */
    public Set<String> savedModes() {
        Set<String> out = new HashSet<String>();
        for (Map.Entry<String, JsonElement> e : profiles.entrySet()) {
            out.add(e.getKey());
        }
        return out;
    }

    /** "Bed Wars profile" for a couple of seconds after a switch, top centre, fading out. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || toast == null) {
            return;
        }
        long age = System.currentTimeMillis() - toastAt;
        if (age > 2500) {
            toast = null;
            return;
        }
        float alpha = age < 200 ? age / 200f : age > 2000 ? (2500 - age) / 500f : 1f;
        ScaledResolution sr = new ScaledResolution(mc);
        int w = Draw.width(toast) + 16;
        float x = (sr.getScaledWidth() - w) / 2f;
        float y = 6 + (1 - Math.min(1, age / 200f)) * -6;
        Draw.round(x, y, x + w, y + 16, 8, Draw.withAlpha(0xFF0C1019, Math.round(alpha * 0xE0)));
        Draw.roundOutline(x, y, x + w, y + 16, 8, Draw.withAlpha(0xFF5B8CFF, Math.round(alpha * 0xCC)));
        Draw.text(toast, x + 8, y + 4, Draw.withAlpha(0xFFE8ECF4, Math.max(8, Math.round(alpha * 255))));
    }
}
