package com.novomc.modern;

import com.google.gson.JsonElement;
import com.novomc.client.config.Catalog;
import net.minecraft.client.Minecraft;

/**
 * A Novo Client mod on modern Minecraft. Name, defaults and settings come
 * from the shared catalog.json; on/off state and settings from the shared
 * {@code config/novomc/modules.json}, exactly as on 1.8.9.
 */
public abstract class Module {
    protected final Minecraft mc = Minecraft.getInstance();
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

    final void setEnabled(boolean on) {
        if (on != enabled) {
            enabled = on;
            if (on) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    protected void onEnable() {}

    protected void onDisable() {}

    /** Every client tick while enabled. */
    public void tick() {}

    protected boolean bool(String key) {
        JsonElement e = NovoModern.config().setting(def, key);
        return e != null && e.getAsBoolean();
    }

    protected float number(String key) {
        JsonElement e = NovoModern.config().setting(def, key);
        return e != null ? e.getAsFloat() : 0;
    }

    protected String choice(String key) {
        JsonElement e = NovoModern.config().setting(def, key);
        return e != null ? e.getAsString() : "";
    }

    /** A `#RRGGBB` setting as opaque ARGB. */
    protected int color(String key) {
        JsonElement e = NovoModern.config().setting(def, key);
        try {
            return 0xFF000000 | Integer.parseInt(e.getAsString().replace("#", ""), 16);
        } catch (RuntimeException ex) {
            return 0xFFFFFFFF;
        }
    }
}
