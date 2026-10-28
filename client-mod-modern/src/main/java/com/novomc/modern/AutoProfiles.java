package com.novomc.modern;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.novomc.client.config.Catalog;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Auto Mode Profiles on modern Minecraft: works out the PvP mode from your
 * kit and keeps a separate setup for each (mods, HUD layout, settings,
 * keybinds), in the same profiles.json format as 1.8.9.
 */
public final class AutoProfiles extends Module {
    public enum Mode {
        GENERAL("general", "General"),
        CRYSTAL("crystal", "Crystal PvP"),
        MACE("mace", "Mace"),
        SPEAR("spear", "Spear"),
        NETHPOT("nethpot", "NethPot"),
        UHC("uhc", "UHC"),
        AXE("axe", "Axe"),
        SWORD("sword", "Sword");

        public final String id;
        public final String label;

        Mode(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final int STABLE_CHECKS = 3;
    private static final Set<String> GLOBAL = Set.of("auto_profiles", "fullbright");

    private final File file = new File(NovoModern.gameDir(), "config/novomc/profiles.json");
    private JsonObject profiles = new JsonObject();
    private Mode current;
    private Mode pending;
    private int pendingCount;
    private int ticks;
    private String toast;
    private long toastAt;

    public AutoProfiles() {
        super("auto_profiles");
    }

    /** The mode a kit is for, by the items it carries. */
    static Mode detect(Inventory inv) {
        boolean crystal = false, base = false, mace = false, spear = false, sword = false, axe = false, shield = false;
        boolean lava = false, gapple = false, netherite = false;
        int splashes = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (id.equals("end_crystal")) crystal = true;
            else if (id.equals("obsidian") || id.equals("respawn_anchor")) base = true;
            else if (id.equals("mace")) mace = true;
            else if (id.endsWith("_spear")) spear = true;
            else if (id.endsWith("_sword")) sword = true;
            else if (id.endsWith("_axe")) axe = true;
            else if (id.equals("shield")) shield = true;
            else if (id.equals("lava_bucket")) lava = true;
            else if (id.equals("golden_apple")) gapple = true;
            else if (id.equals("splash_potion")) splashes += s.getCount();
            if (id.startsWith("netherite_")) netherite = true;
        }
        if (crystal && base) return Mode.CRYSTAL;
        if (mace) return Mode.MACE;
        if (spear) return Mode.SPEAR;
        if (splashes >= 4 && netherite) return Mode.NETHPOT;
        if (sword && lava && gapple) return Mode.UHC;
        if (axe && shield && !sword) return Mode.AXE;
        if (sword) return Mode.SWORD;
        return Mode.GENERAL;
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
    public void tick() {
        if (++ticks % 20 != 0) {
            return;
        }
        Mode mode = mc.player == null ? Mode.GENERAL : detect(mc.player.getInventory());
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
    }

    public Mode currentMode() {
        return current;
    }

    private static List<String> profiled() {
        List<String> ids = new ArrayList<String>();
        for (Catalog.Def def : Catalog.all()) {
            if (def.modern && !def.category.equals("performance") && !GLOBAL.contains(def.id)) {
                ids.add(def.id);
            }
        }
        return ids;
    }

    private void store(Mode mode) {
        JsonObject profile = new JsonObject();
        profile.add("modules", NovoModern.config().snapshot(profiled()));
        JsonObject keys = new JsonObject();
        for (KeyMapping k : mc.options.keyMappings) {
            keys.addProperty(k.getName(), k.saveString());
        }
        // Key codes differ between 1.8.9 and modern, so modern keys are kept apart.
        profile.add("modernKeys", keys);
        profiles.add(mode.id, profile);
    }

    private void apply(JsonObject profile) {
        if (profile.has("modules") && profile.get("modules").isJsonObject()) {
            NovoModern.config().restore(profile.getAsJsonObject("modules"));
            NovoModern.config().save();
            NovoModern.apply();
        }
        if (profile.has("modernKeys") && profile.get("modernKeys").isJsonObject()) {
            JsonObject keys = profile.getAsJsonObject("modernKeys");
            for (KeyMapping k : mc.options.keyMappings) {
                JsonElement v = keys.get(k.getName());
                if (v != null && v.isJsonPrimitive()) {
                    k.setKey(InputConstants.getKey(v.getAsString()));
                }
            }
            KeyMapping.resetMapping();
            mc.options.save();
        }
    }

    private void load() {
        profiles = new JsonObject();
        if (!file.isFile()) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
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
            Files.writeString(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't save " + file + ": " + e);
        }
    }

    /** The "Mace profile" notice, top centre, fading out. */
    void renderToast(GuiGraphicsExtractor g) {
        if (toast == null) {
            return;
        }
        long age = System.currentTimeMillis() - toastAt;
        if (age > 2500) {
            toast = null;
            return;
        }
        float alpha = age < 200 ? age / 200f : age > 2000 ? (2500 - age) / 500f : 1f;
        int a = Math.max(8, Math.round(alpha * 255));
        int w = mc.font.width(toast) + 16;
        int x = (g.guiWidth() - w) / 2;
        g.fill(x, 6, x + w, 22, (Math.round(alpha * 0xE0) << 24) | 0x0C1019);
        g.outline(x, 6, w, 16, (Math.round(alpha * 0xCC) << 24) | 0x5B8CFF);
        g.text(mc.font, toast, x + 8, 10, (a << 24) | 0xE8ECF4, false);
    }
}
