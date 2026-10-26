package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

/**
 * Pressing the sprint key toggles sprinting (and optionally the sneak key
 * toggles sneaking). Works by holding the vanilla key down, so vanilla still
 * decides when sprinting is allowed.
 */
public class ToggleSprintModule extends TextHud {
    private boolean sprint;
    private boolean sneak;

    public ToggleSprintModule() {
        super("toggle_sprint");
    }

    @Override
    public void onTick() {
        if (mc.thePlayer == null) {
            return;
        }
        GameSettings gs = mc.gameSettings;
        if (gs.keyBindSprint.isPressed()) {
            sprint = !sprint;
        }
        if (bool("toggleSneak")) {
            if (gs.keyBindSneak.isPressed()) {
                sneak = !sneak;
            }
        } else if (sneak) {
            sneak = false;
            KeyBinding.setKeyBindState(gs.keyBindSneak.getKeyCode(), false);
        }
        if (sprint) {
            KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), true);
        }
        if (sneak) {
            KeyBinding.setKeyBindState(gs.keyBindSneak.getKeyCode(), true);
        }
    }

    @Override
    protected void onDisable() {
        GameSettings gs = mc.gameSettings;
        if (sprint) {
            KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), false);
        }
        if (sneak) {
            KeyBinding.setKeyBindState(gs.keyBindSneak.getKeyCode(), false);
        }
        sprint = sneak = false;
    }

    private String status() {
        if (sneak) {
            return "[Sneaking (Toggled)]";
        }
        if (sprint) {
            return "[Sprinting (Toggled)]";
        }
        if (mc.thePlayer != null && mc.thePlayer.isSprinting()) {
            return "[Sprinting (Vanilla)]";
        }
        return "";
    }

    @Override
    public boolean visible() {
        return bool("showStatus") && !status().isEmpty();
    }

    @Override
    protected String[] lines(boolean preview) {
        String s = status();
        return new String[] {s.isEmpty() ? "[Sprinting (Toggled)]" : s};
    }
}
