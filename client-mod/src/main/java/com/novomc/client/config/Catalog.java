package com.novomc.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module definitions from {@code assets/novomc/catalog.json}: names, defaults
 * and settings. The NovoMC launcher builds its Mod Library from the same file.
 */
public final class Catalog {
    /** One user-editable setting, as the mods screen shows it. */
    public static final class Setting {
        public final String key;
        /** toggle, color, number, text or choice. */
        public final String type;
        public final String label;
        public final float min;
        public final float max;
        public final float step;
        public final int maxLength;
        /** For choice settings: {value, label} pairs. */
        public final List<String[]> options = new ArrayList<String[]>();

        Setting(JsonObject o) {
            key = o.get("key").getAsString();
            type = o.get("type").getAsString();
            label = o.get("label").getAsString();
            min = o.has("min") ? o.get("min").getAsFloat() : 0;
            max = o.has("max") ? o.get("max").getAsFloat() : 1;
            step = o.has("step") ? o.get("step").getAsFloat() : 1;
            maxLength = o.has("maxLength") ? o.get("maxLength").getAsInt() : 32;
            if (o.has("options")) {
                for (JsonElement e : o.getAsJsonArray("options")) {
                    JsonObject opt = e.getAsJsonObject();
                    options.add(new String[] {opt.get("value").getAsString(), opt.get("label").getAsString()});
                }
            }
        }
    }

    public static final class Def {
        public final String id;
        public final String name;
        public final String description;
        public final String category;
        public final boolean enabledByDefault;
        public final List<Setting> settings;
        /** Whether the module draws on the HUD (and can be moved). */
        public final boolean hud;
        public final float defaultX;
        public final float defaultY;
        /** Column this module stacks in until the user moves it, or null. */
        public final String stack;
        /** x is the module's center rather than its left edge. */
        public final boolean centered;
        /** Setting key to default value. */
        public final Map<String, JsonElement> defaults;
        /** Also in the modern (26.3) client; every module runs on 1.8.9. */
        public final boolean modern;

        Def(JsonObject o) {
            id = o.get("id").getAsString();
            name = o.get("name").getAsString();
            description = o.has("description") ? o.get("description").getAsString() : "";
            category = o.get("category").getAsString();
            enabledByDefault = o.get("enabled").getAsBoolean();
            modern = o.has("modern") && o.get("modern").getAsBoolean();
            JsonObject pos = o.has("hud") ? o.getAsJsonObject("hud") : null;
            hud = pos != null;
            defaultX = pos != null ? pos.get("x").getAsFloat() : 0;
            defaultY = pos != null ? pos.get("y").getAsFloat() : 0;
            stack = pos != null && pos.has("stack") ? pos.get("stack").getAsString() : null;
            centered = pos != null && pos.has("center") && pos.get("center").getAsBoolean();
            Map<String, JsonElement> d = new LinkedHashMap<String, JsonElement>();
            List<Setting> s = new ArrayList<Setting>();
            for (JsonElement e : o.getAsJsonArray("settings")) {
                JsonObject setting = e.getAsJsonObject();
                d.put(setting.get("key").getAsString(), setting.get("default"));
                s.add(new Setting(setting));
            }
            defaults = Collections.unmodifiableMap(d);
            settings = Collections.unmodifiableList(s);
        }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<String, Def>();
    /** Category id to display name, in catalog order. */
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<String, String>();

    private Catalog() {}

    public static void load() {
        InputStream in = Catalog.class.getResourceAsStream("/assets/novomc/catalog.json");
        if (in == null) {
            throw new IllegalStateException("NovoMC catalog.json is missing from the mod jar");
        }
        JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray modules = root.getAsJsonArray("modules");
        DEFS.clear();
        for (JsonElement m : modules) {
            Def def = new Def(m.getAsJsonObject());
            DEFS.put(def.id, def);
        }
        CATEGORIES.clear();
        for (JsonElement c : root.getAsJsonArray("categories")) {
            JsonObject cat = c.getAsJsonObject();
            CATEGORIES.put(cat.get("id").getAsString(), cat.get("name").getAsString());
        }
    }

    public static Map<String, String> categories() {
        return Collections.unmodifiableMap(CATEGORIES);
    }

    public static Def get(String id) {
        Def def = DEFS.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Module " + id + " is not in catalog.json");
        }
        return def;
    }

    public static List<Def> all() {
        return new ArrayList<Def>(DEFS.values());
    }
}
