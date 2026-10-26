package com.novomc.client.modules;

import com.novomc.client.module.HudModule;
import com.novomc.client.util.Draw;
import net.minecraft.util.MathHelper;

/** Compass strip; Minecraft yaw 0 is south, 90 west, 180 north, -90 east. */
public class DirectionModule extends HudModule {
    private static final int W = 140;
    private static final int H = 18;
    /** Degrees visible across the strip. */
    private static final float SPAN = 180f;
    private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public DirectionModule() {
        super("direction");
    }

    @Override
    public int width(boolean preview) {
        return W;
    }

    @Override
    public int height(boolean preview) {
        return H;
    }

    @Override
    public void render(boolean preview) {
        float yaw = mc.thePlayer != null ? MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) : 180f;
        int marker = color("markerColor");
        int text = color("textColor");
        Draw.rect(0, 0, W, H, 0x6F000000);

        for (int deg = -180; deg < 180; deg += 15) {
            float diff = MathHelper.wrapAngleTo180_float(deg - yaw);
            if (Math.abs(diff) > SPAN / 2 - 4) {
                continue;
            }
            float x = W / 2f + diff * W / SPAN;
            if (deg % 45 == 0) {
                String label = LABELS[((deg + 360) % 360) / 45];
                boolean north = "N".equals(label);
                Draw.text(label, x - Draw.width(label) / 2f, 5, north ? marker : label.length() == 1 ? text : Draw.withAlpha(text, 0xAA));
            } else {
                Draw.rect(Math.round(x), H - 5, Math.round(x) + 1, H - 2, Draw.withAlpha(text, 0x66));
            }
        }
        // Center marker.
        Draw.rect(W / 2, 0, W / 2 + 1, 3, marker);
        Draw.rect(W / 2, H - 2, W / 2 + 1, H, marker);
    }
}
