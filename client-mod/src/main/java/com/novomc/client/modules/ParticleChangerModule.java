package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Extra crit / sharpness particles on your hits (client-side only). */
public class ParticleChangerModule extends Module {
    public ParticleChangerModule() {
        super("particle_changer");
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || event.entityPlayer != mc.thePlayer || !(event.target instanceof EntityLivingBase)) {
            return;
        }
        EntityPlayer p = event.entityPlayer;
        boolean crit = p.fallDistance > 0 && !p.onGround && !p.isOnLadder() && !p.isInWater()
                && !p.isPotionActive(Potion.blindness) && p.ridingEntity == null;
        ItemStack held = p.getHeldItem();
        boolean sharp = held != null && EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, held) > 0;
        int extra = Math.max(0, integer("multiplier") - 1);

        // Vanilla already spawns one burst when the hit really is a crit / sharpness hit.
        int crits = crit ? extra : bool("alwaysCrit") ? extra + 1 : 0;
        int magic = sharp ? extra : bool("alwaysSharpness") ? extra + 1 : 0;
        for (int i = 0; i < crits; i++) {
            mc.effectRenderer.emitParticleAtEntity(event.target, EnumParticleTypes.CRIT);
        }
        for (int i = 0; i < magic; i++) {
            mc.effectRenderer.emitParticleAtEntity(event.target, EnumParticleTypes.CRIT_MAGIC);
        }
    }
}
