package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Hits in a row: a hit counts when your target shows its hurt animation right
 * after you attack; getting hurt yourself or 2.5 s without a hit resets it.
 */
public class ComboCounterModule extends TextHud {
    private int combo;
    private long lastHit;
    private Entity target;
    private long attackedAt;
    private boolean counted;
    private int lastOwnHurt;

    public ComboCounterModule() {
        super("combo_counter");
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (isEnabled() && event.entityPlayer == mc.thePlayer) {
            target = event.target;
            attackedAt = System.currentTimeMillis();
            counted = false;
        }
    }

    @Override
    public void onTick() {
        if (mc.thePlayer == null) {
            combo = 0;
            return;
        }
        long now = System.currentTimeMillis();
        int hurt = mc.thePlayer.hurtTime;
        if (hurt > lastOwnHurt) {
            combo = 0;
        }
        lastOwnHurt = hurt;
        if (target instanceof EntityLivingBase && !counted && now - attackedAt < 500) {
            EntityLivingBase t = (EntityLivingBase) target;
            if (t.maxHurtTime > 0 && t.hurtTime > t.maxHurtTime - 3) {
                combo++;
                lastHit = now;
                counted = true;
            }
        }
        if (now - lastHit > 2500) {
            combo = 0;
        }
    }

    @Override
    public boolean visible() {
        return combo > 0;
    }

    @Override
    protected String[] lines(boolean preview) {
        return new String[] {(combo > 0 || !preview ? combo : 3) + " Combo"};
    }
}
