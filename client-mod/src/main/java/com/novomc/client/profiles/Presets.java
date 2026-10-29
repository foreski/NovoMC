package com.novomc.client.profiles;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novomc.client.NovoClient;
import com.novomc.client.modules.AutoProfilesModule;
import net.minecraft.client.Minecraft;

/**
 * Named setups a player saves and switches between by hand, the way Lunar's
 * preset sidebar works: "Skyblock", "Arena PvP", "Recording", whatever they
 * want. A preset is a snapshot of every mod that belongs to a setup — the same
 * set Auto Mode Profiles switches between, so performance and rendering mods
 * that would rebuild chunks mid-game stay out of it.
 *
 * Stored in {@code config/novomc/presets.json}. The most recent save is applied
 * lazily by id, and the sidebar in {@link com.novomc.client.gui.GuiModules}
 * reads {@link #list()} to draw itself.
 */
public final class Presets {
    /** One saved setup, as the sidebar shows it. */
    public static final class Preset {
        public final String id;
        public final String name;
        public final long savedAt;

        Preset(String id, String name, long savedAt) {
            this.id = id;
            this.name = name;
            this.savedAt = savedAt;
        }
    }

    private static final File FILE = new File(Minecraft.getMinecraft().mcDataDir, "config/novomc/presets.json");

    private Presets() {}

    /** Saved presets, newest first. */
    public static List<Preset> list() {
        List<Preset> out = new ArrayList<Preset>();
        JsonArray array = load().getAsJsonArray("presets");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject preset = element.getAsJsonObject();
            out.add(new Preset(
                    preset.has("id") ? preset.get("id").getAsString() : "",
                    preset.has("name") ? preset.get("name").getAsString() : "Preset",
                    preset.has("savedAt") ? preset.get("savedAt").getAsLong() : 0L));
        }
        return out;
    }

    /** Snapshots the current setup under a name and returns the new preset's id. */
    public static String save(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            clean = "Preset " + (list().size() + 1);
        }
        if (clean.length() > 24) {
            clean = clean.substring(0, 24);
        }
        JsonObject root = load();
        JsonArray array = root.getAsJsonArray("presets");
        String id = "p" + Long.toString(System.currentTimeMillis(), 36);
        JsonObject preset = new JsonObject();
        preset.addProperty("id", id);
        preset.addProperty("name", clean);
        preset.addProperty("savedAt", System.currentTimeMillis());
        preset.add("modules", NovoClient.config().snapshot(AutoProfilesModule.profiled()));
        array.add(preset);
        write(root);
        return id;
    }

    /** Replaces a preset's contents with the current setup, keeping its name. */
    public static void overwrite(String id) {
        JsonObject root = load();
        for (JsonElement element : root.getAsJsonArray("presets")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject preset = element.getAsJsonObject();
            if (preset.has("id") && id.equals(preset.get("id").getAsString())) {
                preset.add("modules", NovoClient.config().snapshot(AutoProfilesModule.profiled()));
                preset.addProperty("savedAt", System.currentTimeMillis());
                write(root);
                return;
            }
        }
    }

    /** Puts the preset's setup back and returns its name, or null when it is gone. */
    public static String apply(String id) {
        JsonObject root = load();
        for (JsonElement element : root.getAsJsonArray("presets")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject preset = element.getAsJsonObject();
            if (!preset.has("id") || !id.equals(preset.get("id").getAsString())) {
                continue;
            }
            JsonElement modules = preset.get("modules");
            if (modules != null && modules.isJsonObject()) {
                NovoClient.config().restore(modules.getAsJsonObject());
                NovoClient.save();
                NovoClient.apply();
            }
            return preset.has("name") ? preset.get("name").getAsString() : "Preset";
        }
        return null;
    }

    public static void remove(String id) {
        JsonObject root = load();
        JsonArray array = root.getAsJsonArray("presets");
        JsonArray kept = new JsonArray();
        for (JsonElement element : array) {
            if (element.isJsonObject() && element.getAsJsonObject().has("id")
                    && id.equals(element.getAsJsonObject().get("id").getAsString())) {
                continue;
            }
            kept.add(element);
        }
        root.add("presets", kept);
        write(root);
    }

    // ---- Storage ----

    private static JsonObject load() {
        JsonObject root = new JsonObject();
        root.add("presets", new JsonArray());
        if (!FILE.isFile()) {
            return root;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(FILE.toPath()), StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed.isJsonObject()) {
                JsonObject found = parsed.getAsJsonObject();
                if (found.has("presets") && found.get("presets").isJsonArray()) {
                    return found;
                }
            }
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't read " + FILE + ": " + e);
        }
        return root;
    }

    private static void write(JsonObject root) {
        try {
            FILE.getParentFile().mkdirs();
            root.addProperty("version", 1);
            Files.write(FILE.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't save " + FILE + ": " + e);
        }
    }
}
