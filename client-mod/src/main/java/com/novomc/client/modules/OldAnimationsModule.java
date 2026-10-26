package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * 1.7 Visuals: the arm swings while blocking/eating/drawing (see
 * MixinItemRenderer) and armor flashes red when hurt (MixinLayerArmorBase).
 */
public class OldAnimationsModule extends Module {
    private static volatile boolean blockHit;
    private static volatile boolean armorTint;

    public OldAnimationsModule() {
        super("old_animations");
    }

    public static boolean blockHit() {
        return blockHit;
    }

    public static boolean armorTint() {
        return armorTint;
    }

    @Override
    public void onSettingsChanged() {
        blockHit = bool("blockhit");
        armorTint = bool("armorTint");
    }

    @Override
    protected void onDisable() {
        blockHit = armorTint = false;
    }

    /** Swing locally when attacking while using an item (1.8 ignores those clicks). Visual only. */
    @Override
    public void onTick() {
        EntityPlayerSP p = mc.thePlayer;
        if (!blockHit || p == null || mc.currentScreen != null || !p.isUsingItem() || !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        if (!p.isSwingInProgress || p.swingProgressInt >= 3 || p.swingProgressInt < 0) {
            p.swingProgressInt = -1;
            p.isSwingInProgress = true;
        }
    }
}
