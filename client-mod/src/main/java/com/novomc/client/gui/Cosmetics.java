package com.novomc.client.gui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;

/**
 * `config/novomc/cosmetics.json`: the equipped cape, wings and hat, the owned
 * ones and their names.
 *
 * The launcher writes it before a launch (`src-tauri/src/novo/cosmetics.rs`),
 * because the game has no account token to ask the relay itself. Picking
 * another cosmetic only edits this file; the launcher saves whatever changed
 * to the account after the game exits.
 */
public final class Cosmetics {
    public static final String CAPE = "cape";
    public static final String WING = "wing";
    public static final String HAT = "hat";
    /** How often {@link #current()} looks at the file again. */
    private static final long RELOAD_MS = 1000L;
    private static Cosmetics cached;
    private static long cachedAt;
    /** The three slots, in the order the cosmetics editor's tabs use them. */
    static final String[] KINDS = {CAPE, WING, HAT};
    static final String[] LABELS = {"CAPE", "WINGS", "HAT"};
    private static final String[] LISTS = {"capes", "wings", "hats"};
    private static final String[] TABS = {"Capes", "Wings", "Hats"};

    /** One owned cosmetic, as the launcher wrote it. */
    static final class Entry {
        final String id;
        final String name;

        Entry(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final File file;
    private final JsonObject data;

    private Cosmetics(File file, JsonObject data) {
        this.file = file;
        this.data = data;
    }

    /** The cosmetics for this installation, or null when the launcher never wrote them. */
    static Cosmetics load() {
        File file = new File(Minecraft.getMinecraft().mcDataDir, "config/novomc/cosmetics.json");
        if (!file.isFile()) {
            return null;
        }
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return new Cosmetics(file, new JsonParser().parse(json).getAsJsonObject());
        } catch (Exception e) {
            System.err.println("[NovoMC] Could not read cosmetics.json: " + e);
            return null;
        }
    }

    /**
     * The cosmetics for this installation, or null when the launcher never wrote
     * them. Chased at most once a second, because the renderers ask on every
     * frame and the wardrobe writes the file while the game runs.
     */
    public static Cosmetics current() {
        long now = System.currentTimeMillis();
        if (cached == null || now - cachedAt >= RELOAD_MS) {
            cached = load();
            cachedAt = now;
        }
        return cached;
    }

    static String label(String kind) {
        return LABELS[index(kind)];
    }

    static String tab(String kind) {
        return TABS[index(kind)];
    }

    private static int index(String kind) {
        for (int i = 0; i < KINDS.length; i++) {
            if (KINDS[i].equals(kind)) {
                return i;
            }
        }
        return 0;
    }

    private static String listKey(String kind) {
        return LISTS[index(kind)];
    }

    /** The equipped id in a slot, or null for bare. */
    public String worn(String kind) {
        JsonElement value = data.get(kind);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /** Equips (or clears, with a null id) and writes the file the launcher reads back. */
    void wear(String kind, String id) {
        data.add(kind, id == null ? JsonNull.INSTANCE : new JsonPrimitive(id));
        try {
            Files.write(file.toPath(), data.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[NovoMC] Could not save cosmetics.json: " + e);
        }
    }

    /** Everything owned in a slot, in the order the launcher listed it. */
    List<Entry> owned(String kind) {
        List<Entry> out = new ArrayList<Entry>();
        for (JsonElement element : items(kind)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String id = string(item, "id");
            if (id != null) {
                String name = string(item, "name");
                out.add(new Entry(id, name == null ? id : name));
            }
        }
        return out;
    }

    /** The display name of an equipped id, falling back to the id itself. */
    String nameOf(String kind, String id) {
        if (id == null) {
            return "None";
        }
        for (Entry entry : owned(kind)) {
            if (entry.id.equals(id)) {
                return entry.name;
            }
        }
        return id;
    }

    private JsonArray items(String kind) {
        JsonElement items = data.get("items");
        if (items == null || !items.isJsonObject()) {
            return new JsonArray();
        }
        JsonElement list = items.getAsJsonObject().get(listKey(kind));
        return list != null && list.isJsonArray() ? list.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
