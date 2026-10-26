package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** WASD (+ mouse buttons and space) that light up while held. */
public class KeystrokesModule extends HudModule {
    private static final int K = 22;
    private static final int GAP = 2;
    private static final int W = K * 3 + GAP * 2;
    private static final int SPACE_H = 12;

    /** Per-key press animation, 0 (up) to 1 (down). */
    private final Map<KeyBinding, Float> anim = new HashMap<KeyBinding, Float>();
    private long lastFrame = System.nanoTime();

    public KeystrokesModule() {
        super("keystrokes");
    }

    @Override
    public int width(boolean preview) {
        return W;
    }

    @Override
    public int height(boolean preview) {
        int h = K * 2 + GAP;
        if (bool("showMouse")) {
            h += GAP + K;
        }
        if (bool("showSpace")) {
            h += GAP + SPACE_H;
        }
        return h;
    }

    @Override
    public void render(boolean preview) {
        long now = System.nanoTime();
        float step = Math.min(1f, (now - lastFrame) / 1e9f * 12f);
        lastFrame = now;

        GameSettings gs = mc.gameSettings;
        key(gs.keyBindForward, K + GAP, 0, K, K, null, null, step);
        key(gs.keyBindLeft, 0, K + GAP, K, K, null, null, step);
        key(gs.keyBindBack, K + GAP, K + GAP, K, K, null, null, step);
        key(gs.keyBindRight, (K + GAP) * 2, K + GAP, K, K, null, null, step);
        int y = (K + GAP) * 2;
        if (bool("showMouse")) {
            int half = (W - GAP) / 2;
            boolean cps = bool("showCps");
            key(gs.keyBindAttack, 0, y, half, K, "LMB", cps ? CpsTracker.INSTANCE.left() + " CPS" : null, step);
            key(gs.keyBindUseItem, half + GAP, y, half, K, "RMB", cps ? CpsTracker.INSTANCE.right() + " CPS" : null, step);
            y += K + GAP;
        }
        if (bool("showSpace")) {
            key(gs.keyBindJump, 0, y, W, SPACE_H, "", null, step);
            float p = anim.containsKey(gs.keyBindJump) ? anim.get(gs.keyBindJump) : 0;
            int bar = Draw.blend(color("textColor"), 0xFF000000, p);
            Draw.rect(W / 2 - 12, y + SPACE_H / 2, W / 2 + 12, y + SPACE_H / 2 + 1, bar);
        }
    }

    private void key(KeyBinding kb, int x, int y, int w, int h, String label, String sub, float step) {
        float target = kb.isKeyDown() ? 1f : 0f;
        Float cur = anim.get(kb);
        float p = cur == null ? target : cur + (target - cur) * step;
        anim.put(kb, p);

        Draw.rect(x, y, x + w, y + h, Draw.blend(0x66000000, Draw.withAlpha(color("pressedColor"), 0xD0), p));
        int text = Draw.blend(color("textColor"), 0xFF000000, p);
        String name = label != null ? label : keyName(kb);
        if (sub == null) {
            Draw.text(name, x + (w - Draw.width(name)) / 2f, y + (h - 8) / 2f, text);
            return;
        }
        Draw.text(name, x + (w - Draw.width(name)) / 2f, y + 3, text);
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 1);
        Draw.text(sub, (x + w / 2f) * 2 - Draw.width(sub) / 2f, (y + h - 7) * 2, text);
        GlStateManager.popMatrix();
    }

    private static String keyName(KeyBinding kb) {
        int code = kb.getKeyCode();
        if (code < 0) {
            return "M" + (code + 101);
        }
        String name = Keyboard.getKeyName(code);
        return name == null ? "?" : name.length() > 4 ? name.substring(0, 4) : name;
    }
}
