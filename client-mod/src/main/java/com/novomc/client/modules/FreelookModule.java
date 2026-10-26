package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Hold the freelook key (Left Alt) to turn the camera without turning the
 * player. MixinEntity stops mouse input from rotating the player and
 * MixinEntityRenderer points the camera at these angles instead.
 */
public class FreelookModule extends Module {
    private static volatile boolean active;
    private static float cameraYaw;
    private static float cameraPitch;
    private int savedView = -1;

    public FreelookModule() {
        super("freelook");
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        boolean want = isEnabled() && NovoClient.FREELOOK_KEY.isKeyDown() && mc.currentScreen == null && mc.thePlayer != null;
        if (want && !active) {
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            if (bool("thirdPerson")) {
                savedView = mc.gameSettings.thirdPersonView;
                mc.gameSettings.thirdPersonView = 1;
            }
            active = true;
        } else if (!want && active) {
            stop();
        }
    }

    private void stop() {
        active = false;
        if (savedView >= 0) {
            mc.gameSettings.thirdPersonView = savedView;
            savedView = -1;
        }
    }

    @Override
    protected void onDisable() {
        if (active) {
            stop();
        }
    }

    public static boolean active() {
        return active;
    }

    /** Takes a mouse movement for the camera; false when freelook is off. */
    public static boolean consumeMouse(float yaw, float pitch) {
        if (!active) {
            return false;
        }
        // Same scaling as Entity.setAngles.
        cameraYaw += yaw * 0.15f;
        cameraPitch = Math.max(-90f, Math.min(90f, cameraPitch - pitch * 0.15f));
        return true;
    }

    public static float yaw(Entity entity, float vanilla) {
        return active && entity == Minecraft.getMinecraft().getRenderViewEntity() ? cameraYaw : vanilla;
    }

    public static float pitch(Entity entity, float vanilla) {
        return active && entity == Minecraft.getMinecraft().getRenderViewEntity() ? cameraPitch : vanilla;
    }
}
