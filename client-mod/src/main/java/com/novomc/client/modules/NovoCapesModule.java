package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.net.NovoPresence;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * Novo Capes: the cape each player picked for their Novo account (in the
 * launcher's Accounts page), drawn instead of their Minecraft cape. The
 * textures ship in the jar; {@link NovoPresence} says who wears which.
 */
public class NovoCapesModule extends Module {
    private static volatile boolean on;
    private static volatile boolean showOthers;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();

    public NovoCapesModule() {
        super("novo_capes");
    }

    public static boolean isOn() {
        return on;
    }

    /** The Novo cape texture for this player, or null for their usual cape. */
    public static ResourceLocation capeOf(UUID id) {
        if (!on || id == null) {
            return null;
        }
        String cape = NovoPresence.INSTANCE.cape(id);
        if (cape == null || !(showOthers || id.equals(Minecraft.getMinecraft().getSession().getProfile().getId()))) {
            return null;
        }
        ResourceLocation tex = TEXTURES.get(cape);
        if (tex == null) {
            tex = new ResourceLocation("novomc", "textures/capes/" + cape + ".png");
            TEXTURES.put(cape, tex);
        }
        return tex;
    }

    @Override
    public void onSettingsChanged() {
        showOthers = bool("showOthers");
    }

    @Override
    protected void onEnable() {
        on = true;
        onSettingsChanged();
    }

    @Override
    protected void onDisable() {
        on = false;
    }
}
