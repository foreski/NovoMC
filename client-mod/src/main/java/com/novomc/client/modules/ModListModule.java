package com.novomc.client.modules;

import com.novomc.client.NovoClient;
import com.novomc.client.module.HudModule;
import com.novomc.client.module.Module;
import com.novomc.client.util.Draw;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mod List: an "ArrayList"-style HUD listing your active NovoMC mods down
 * the screen edge, in a moving rainbow or NovoMC's gradient. Right-aligned
 * when placed on the right half of the screen.
 */
public class ModListModule extends HudModule {
    private static final int LINE = 10;
    private static final int PAD = 3;
    private static final String WATERMARK = "Novo Client";
    /** Always-on plumbing that would only clutter the list. */
    private static final Set<String> HIDDEN = new HashSet<String>(Arrays.asList(
            "module_list", "main_menu", "modern_font", "menu_blur", "screenshots", "unfocused_fps", "performance_tweaks"));
    private static final List<String> SAMPLE = Arrays.asList("Freelook", "Toggle Sprint", "Zoom", "Hit Color", "Entity Culling");

    public ModListModule() {
        super("module_list");
    }

    private List<String> names(boolean preview) {
        List<String> out = new ArrayList<String>();
        for (Module m : NovoClient.modules()) {
            if (!m.isEnabled() || HIDDEN.contains(m.id()) || (bool("hideHud") && m instanceof HudModule)) {
                continue;
            }
            out.add(m.def.name);
        }
        if (out.isEmpty() && preview) {
            out.addAll(SAMPLE);
        }
        if ("length".equals(choice("sort"))) {
            Collections.sort(out, new Comparator<String>() {
                public int compare(String a, String b) {
                    return Draw.width(b) - Draw.width(a);
                }
            });
        } else {
            Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        }
        return out;
    }

    private boolean watermark() {
        return bool("watermark");
    }

    @Override
    public boolean visible() {
        return watermark() || !names(false).isEmpty();
    }

    @Override
    public int width(boolean preview) {
        int w = watermark() ? Draw.width(WATERMARK) : 0;
        for (String n : names(preview)) {
            w = Math.max(w, Draw.width(n));
        }
        return w + PAD * 2 + 2;
    }

    @Override
    public int height(boolean preview) {
        return (names(preview).size() + (watermark() ? 1 : 0)) * LINE;
    }

    /** Color for line `i`: a rainbow or gradient wave moving down the list. */
    private int color(int i) {
        String mode = choice("colors");
        if ("white".equals(mode)) {
            return 0xFFFFFFFF;
        }
        float wave = (System.currentTimeMillis() % 4000L) / 4000f - i * 0.06f;
        wave -= (float) Math.floor(wave);
        if ("rainbow".equals(mode)) {
            return Color.HSBtoRGB(wave, 0.75f, 1f) | 0xFF000000;
        }
        // NovoMC gradient: blue -> purple -> cyan and back.
        float t = (float) (0.5 - 0.5 * Math.cos(wave * Math.PI * 2));
        return t < 0.5f ? Draw.blend(0xFF5B8CFF, 0xFFB45BFF, t * 2) : Draw.blend(0xFFB45BFF, 0xFF22D3EE, (t - 0.5f) * 2);
    }

    @Override
    public void render(boolean preview) {
        List<String> lines = new ArrayList<String>(names(preview));
        if (watermark()) {
            lines.add(0, WATERMARK);
        }
        int boxW = width(preview);
        // On the right half of the screen, lines hug the right edge like a classic ArrayList.
        boolean right = x() > 0.5f;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            int w = Draw.width(s) + PAD * 2;
            int left = right ? boxW - w - 2 : 0;
            int top = i * LINE;
            int c = color(i);
            if (bool("background")) {
                Draw.rect(left, top, left + w + 2, top + LINE, 0x80000000);
            }
            if (bool("bar")) {
                int bx = right ? boxW - 2 : left;
                Draw.rect(bx, top, bx + 2, top + LINE, c);
            }
            int tx = right ? left + PAD : left + PAD + (bool("bar") ? 2 : 0);
            Draw.text(s, tx, top + 1, c);
        }
    }
}
