package com.novomc.client.modules;

import com.novomc.client.module.Module;
import java.util.List;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/**
 * Mouse wheel scrolls the tooltip under the cursor. MixinGuiScreen applies
 * the offset; it resets when a different tooltip shows.
 */
public class ScrollableTooltipsModule extends Module {
    private static final int STEP = 12;
    private static volatile boolean on;
    private static int offset;
    private static String shownFirstLine;
    private static long shownAt;

    public ScrollableTooltipsModule() {
        super("scrollable_tooltips");
    }

    /** Called for every tooltip drawn; returns the vertical offset to apply. */
    public static int offsetFor(List<String> lines) {
        if (!on) {
            return 0;
        }
        String first = lines.isEmpty() ? "" : lines.get(0);
        if (!first.equals(shownFirstLine)) {
            shownFirstLine = first;
            offset = 0;
        }
        shownAt = System.currentTimeMillis();
        return offset;
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        int wheel = Mouse.getEventDWheel();
        // Only while a tooltip is on screen, so lists (e.g. creative tabs) still scroll.
        if (!on || wheel == 0 || System.currentTimeMillis() - shownAt > 150) {
            return;
        }
        offset += wheel > 0 ? STEP : -STEP;
        event.setCanceled(true);
    }

    @Override
    protected void onEnable() {
        on = true;
    }

    @Override
    protected void onDisable() {
        on = false;
        offset = 0;
    }
}
