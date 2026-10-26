package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/** Active effects: icon, name with level, and time left. */
public class PotionEffectsModule extends HudModule {
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
    private static final int ROW = 22;

    public PotionEffectsModule() {
        super("potion_effects");
    }

    private List<PotionEffect> effects(boolean preview) {
        List<PotionEffect> out = new ArrayList<PotionEffect>();
        if (mc.thePlayer != null) {
            out.addAll(mc.thePlayer.getActivePotionEffects());
        }
        if (out.isEmpty() && preview) {
            out.add(new PotionEffect(Potion.moveSpeed.id, 2400, 1));
            out.add(new PotionEffect(Potion.fireResistance.id, 3600, 0));
        }
        return out;
    }

    private static String name(PotionEffect e) {
        String name = I18n.format(Potion.potionTypes[e.getPotionID()].getName());
        return e.getAmplifier() > 0 ? name + " " + I18n.format("enchantment.level." + (e.getAmplifier() + 1)) : name;
    }

    private int textX() {
        return bool("showIcons") ? 21 : 0;
    }

    @Override
    public boolean visible() {
        return !effects(false).isEmpty();
    }

    @Override
    public int width(boolean preview) {
        int w = 18;
        for (PotionEffect e : effects(preview)) {
            w = Math.max(w, textX() + Math.max(Draw.width(name(e)), Draw.width(Potion.getDurationString(e))));
        }
        return w;
    }

    @Override
    public int height(boolean preview) {
        return Math.max(1, effects(preview).size()) * ROW - 4;
    }

    @Override
    public void render(boolean preview) {
        int y = 0;
        int color = color("textColor");
        for (PotionEffect e : effects(preview)) {
            // Fade out on alternate beats when about to expire.
            boolean dim = !preview && bool("blinkWhenLow") && e.getDuration() < 200 && (System.currentTimeMillis() / 300) % 2 == 0;
            int alpha = dim ? 0x55 : 0xFF;
            Potion potion = Potion.potionTypes[e.getPotionID()];
            if (bool("showIcons") && potion.hasStatusIcon()) {
                mc.getTextureManager().bindTexture(INVENTORY);
                GlStateManager.enableBlend();
                GlStateManager.color(1, 1, 1, alpha / 255f);
                int idx = potion.getStatusIconIndex();
                Draw.GUI.drawTexturedModalRect(0, y, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18);
                GlStateManager.color(1, 1, 1, 1);
            }
            Draw.text(name(e), textX(), y + 1, Draw.withAlpha(color, alpha));
            Draw.text(Potion.getDurationString(e), textX(), y + 10, Draw.withAlpha(0xAAAAAA, alpha));
            y += ROW;
        }
    }
}
