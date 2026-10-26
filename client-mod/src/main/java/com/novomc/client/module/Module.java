package com.novomc.client.module;

import com.google.gson.JsonElement;
import com.novomc.client.NovoClient;
import com.novomc.client.config.Catalog;
import com.novomc.client.util.Draw;
import net.minecraft.client.Minecraft;

/**
 * A feature that can be switched on and off. Its name, defaults and settings
 * come from catalog.json; the user's values from {@link com.novomc.client.config.ModConfig}.
 *
 * Modules are registered on the Forge event bus once; event handlers must
 * check {@link #isEnabled()} themselves.
 */
public abstract class Module {
    protected final Minecraft mc = Minecraft.getMinecraft();
    public final Catalog.Def def;
    private boolean enabled;

    protected Module(String id) {
        def = Catalog.get(id);
    }

    public String id() {
        return def.id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean on) {
        if (on == enabled) {
            return;
        }
        enabled = on;
        if (on) {
            onEnable();
        } else {
            onDisable();
        }
    }

    protected void onEnable() {}

    protected void onDisable() {}

    /** Called after the config (re)loads while the module is enabled. */
    public void onSettingsChanged() {}

    /** Called every client tick (end phase) while enabled. */
    public void onTick() {}

    // ---- Settings ----

    private JsonElement setting(String key) {
        return NovoClient.config().setting(def, key);
    }

    protected boolean bool(String key) {
        JsonElement e = setting(key);
        return e != null && e.getAsBoolean();
    }

    protected float number(String key) {
        JsonElement e = setting(key);
        return e != null ? e.getAsFloat() : 0;
    }

    protected int integer(String key) {
        return Math.round(number(key));
    }

    /** A free-text setting. */
    protected String text(String key) {
        return choice(key);
    }

    protected String choice(String key) {
        JsonElement e = setting(key);
        return e != null ? e.getAsString() : "";
    }

    /** A `#RRGGBB` setting as an opaque ARGB int. */
    protected int color(String key) {
        JsonElement e = setting(key);
        return Draw.parseColor(e != null ? e.getAsString() : null, 0xFFFFFFFF);
    }
}
