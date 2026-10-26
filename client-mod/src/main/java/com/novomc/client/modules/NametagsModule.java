package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/** Own nametag in third person and nametag background opacity (MixinRender*). */
public class NametagsModule extends Module {
    private static volatile boolean on;
    private static volatile boolean showOwn;
    private static volatile float background = 0.25f;

    public NametagsModule() {
        super("nametags");
    }

    public static boolean showOwn(EntityLivingBase entity) {
        Minecraft mc = Minecraft.getMinecraft();
        return on && showOwn && entity == mc.thePlayer && mc.gameSettings.thirdPersonView != 0;
    }

    public static float backgroundAlpha(float vanilla) {
        return on ? background : vanilla;
    }

    @Override
    public void onSettingsChanged() {
        showOwn = bool("showOwn");
        background = number("background") / 100f;
        on = true;
    }

    @Override
    protected void onDisable() {
        on = false;
    }
}
