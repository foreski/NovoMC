package com.novomc.client.modules;

import com.novomc.client.gui.Cosmetics;
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
 *
 * Animated capes ship one PNG per frame (`textures/capes/aurora_flow_0.png`, ...).
 * {@link #capeOf} picks the frame from the wall clock, so there is no ticking
 * and capes nobody is drawing cost nothing; every viewer shows the same frame
 * and the textures load once, the first time that frame is used.
 */
public class NovoCapesModule extends Module {
    private static volatile boolean on;
    private static volatile boolean showOthers;
    private static volatile boolean physics;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
    /** Animated capes: id -> {frames, frame duration in ms}. Keep in step with relay/src/capes.js. */
    private static final Map<String, int[]> ANIMATED = new HashMap<String, int[]>();

    static {
        ANIMATED.put("aurora_flow", new int[] {24, 83});
        ANIMATED.put("ember_flow", new int[] {24, 83});
    }

    public NovoCapesModule() {
        super("novo_capes");
    }

    public static boolean isOn() {
        return on;
    }

    /** Whether capes hang as cloth instead of as vanilla's flat slab. */
    public static boolean physics() {
        return on && physics;
    }

    /** Whether other players' Novo cosmetics are shown, or only our own. */
    public static boolean showsOthers() {
        return showOthers;
    }

    /** The Novo cape texture for this player, or null for their usual cape. */
    public static ResourceLocation capeOf(UUID id) {
        if (!on || id == null) {
            return null;
        }
        boolean self = id.equals(Minecraft.getMinecraft().getSession().getProfile().getId());
        if (!showOthers && !self) {
            return null;
        }
        // Our own cape comes from `cosmetics.json`, like our wings and hat, so it
        // stays on us when we don't share and when the relay is unreachable.
        Cosmetics local = self ? Cosmetics.current() : null;
        String cape = local != null ? local.worn(Cosmetics.CAPE) : NovoPresence.INSTANCE.cape(id);
        return cape == null ? null : capeTexture(cape);
    }

    /** The texture for a cape id, on the current frame for animated capes. */
    public static ResourceLocation capeTexture(String cape) {
        int[] animation = ANIMATED.get(cape);
        if (animation == null) {
            return texture(cape);
        }
        int frame = (int) ((System.currentTimeMillis() / animation[1]) % animation[0]);
        return texture(cape + "_" + frame);
    }

    /** The cached texture for a file name (without `.png`); it loads on first use. */
    private static ResourceLocation texture(String file) {
        ResourceLocation tex = TEXTURES.get(file);
        if (tex == null) {
            tex = new ResourceLocation("novomc", "textures/capes/" + file + ".png");
            TEXTURES.put(file, tex);
        }
        return tex;
    }

    @Override
    public void onSettingsChanged() {
        showOthers = bool("showOthers");
        physics = bool("capePhysics");
    }

    @Override
    protected void onEnable() {
        on = true;
        onSettingsChanged();
    }

    @Override
    protected void onDisable() {
        on = false;
        physics = false;
    }
}
