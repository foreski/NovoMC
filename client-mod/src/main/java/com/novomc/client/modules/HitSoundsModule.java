package com.novomc.client.modules;

import com.novomc.client.module.Module;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** A sound when you hit something, and another when what you hit dies. Only you hear them. */
public class HitSoundsModule extends Module {
    /** Kills count if the target dies within this many ticks of your last hit. */
    private static final int KILL_WINDOW = 60;

    private EntityLivingBase target;
    private int sinceHit;
    /** Sounds played, for the dev mod check. */
    public int played;

    public HitSoundsModule() {
        super("hit_sounds");
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || event.entityPlayer != mc.thePlayer || !(event.target instanceof EntityLivingBase)) {
            return;
        }
        play(choice("sound"), 1f);
        target = (EntityLivingBase) event.target;
        sinceHit = 0;
    }

    @Override
    public void onTick() {
        if (target == null) {
            return;
        }
        if (++sinceHit > KILL_WINDOW) {
            target = null;
        } else if (target.isDead || target.getHealth() <= 0) {
            if (bool("killSound")) {
                play("random.levelup", 1f);
            }
            target = null;
        }
    }

    private void play(String sound, float pitch) {
        if (mc.thePlayer == null || sound.isEmpty()) {
            return;
        }
        float volume = number("volume") / 100f;
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(sound), volume, pitch,
                (float) mc.thePlayer.posX, (float) mc.thePlayer.posY, (float) mc.thePlayer.posZ));
        played++;
    }
}
