package com.novomc.client.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The user's module choices: {@code config/novomc/modules.json} in the game
 * folder. Shared with the NovoMC launcher, which may edit it while the game
 * runs; {@link #reloadIfChanged()} picks those edits up.
 *
 * <pre>
 * { "version": 1, "modules": { "fps": { "enabled": true, "x": 0.01, "y": 0.02, "scale": 1,
 *                                     "settings": { "textColor": "#FFFFFF" } } } }
 * </pre>
 */
public final class ModConfig {
    private final File file;
    private JsonObject root = new JsonObject();
    private long loadedStamp;

    public ModConfig(File gameDir) {
        file = new File(gameDir, "config/novomc/modules.json");
    }

    /** Reads the file; a missing or broken file means "all defaults". */
    public void load() {
        loadedStamp = file.lastModified();
        root = new JsonObject();
        if (!file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed.isJsonObject()) {
                root = parsed.getAsJsonObject();
            }
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't read " + file + ", using defaults: " + e);
        } finally {
            closeQuietly(reader);
        }
    }

    /** True if the file changed on disk since it was last loaded or saved. */
    public boolean reloadIfChanged() {
        if (file.lastModified() == loadedStamp) {
            return false;
        }
        load();
        return true;
    }

    public void save() {
        file.getParentFile().mkdirs();
        File tmp = new File(file.getPath() + ".tmp");
        Writer writer = null;
        try {
            root.addProperty("version", 1);
            writer = new OutputStreamWriter(Files.newOutputStream(tmp.toPath()), StandardCharsets.UTF_8);
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            writer.close();
            writer = null;
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            loadedStamp = file.lastModified();
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't save " + file + ": " + e);
        } finally {
            closeQuietly(writer);
        }
    }

    private JsonObject module(String id) {
        if (!root.has("modules") || !root.get("modules").isJsonObject()) {
            root.add("modules", new JsonObject());
        }
        JsonObject modules = root.getAsJsonObject("modules");
        if (!modules.has(id) || !modules.get(id).isJsonObject()) {
            modules.add(id, new JsonObject());
        }
        return modules.getAsJsonObject(id);
    }

    public boolean enabled(Catalog.Def def) {
        JsonElement e = module(def.id).get("enabled");
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def.enabledByDefault;
    }

    public void setEnabled(String id, boolean enabled) {
        module(id).addProperty("enabled", enabled);
    }

    public float number(String id, String key, float fallback) {
        JsonElement e = module(id).get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsFloat() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public void setNumber(String id, String key, float value) {
        module(id).addProperty(key, value);
    }

    public boolean has(String id, String key) {
        return module(id).has(key);
    }

    public void remove(String id, String key) {
        module(id).remove(key);
    }

    public void setSetting(String id, String key, JsonElement value) {
        JsonObject m = module(id);
        if (!m.has("settings") || !m.get("settings").isJsonObject()) {
            m.add("settings", new JsonObject());
        }
        m.getAsJsonObject("settings").add(key, value);
    }

    /** A setting's value, falling back to the catalog default. */
    public JsonElement setting(Catalog.Def def, String key) {
        JsonObject m = module(def.id);
        if (m.has("settings") && m.get("settings").isJsonObject()) {
            JsonElement v = m.getAsJsonObject("settings").get(key);
            if (v != null && v.isJsonPrimitive()) {
                return v;
            }
        }
        return def.defaults.get(key);
    }

    /** A deep copy of the given modules' entries (enabled state, HUD position, settings). */
    public JsonObject snapshot(Iterable<String> ids) {
        JsonObject out = new JsonObject();
        for (String id : ids) {
            out.add(id, new JsonParser().parse(module(id).toString()));
        }
        return out;
    }

    /** Puts back entries from {@link #snapshot}; modules missing from it are left alone. */
    public void restore(JsonObject snapshot) {
        JsonObject modules = root.getAsJsonObject("modules");
        if (modules == null) {
            module("_");
            modules = root.getAsJsonObject("modules");
        }
        for (java.util.Map.Entry<String, JsonElement> e : snapshot.entrySet()) {
            modules.add(e.getKey(), new JsonParser().parse(e.getValue().toString()));
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // nothing useful to do
            }
        }
    }
}
