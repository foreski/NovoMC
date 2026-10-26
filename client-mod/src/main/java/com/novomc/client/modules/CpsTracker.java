package com.novomc.client.modules;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Counts mouse clicks in the last second, for CPS and Keystrokes. */
public final class CpsTracker {
    public static final CpsTracker INSTANCE = new CpsTracker();

    private final Deque<Long> left = new ArrayDeque<Long>();
    private final Deque<Long> right = new ArrayDeque<Long>();

    private CpsTracker() {}

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!event.buttonstate) {
            return;
        }
        if (event.button == 0) {
            left.addLast(System.currentTimeMillis());
        } else if (event.button == 1) {
            right.addLast(System.currentTimeMillis());
        }
    }

    public int left() {
        return count(left);
    }

    public int right() {
        return count(right);
    }

    private static int count(Deque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.pollFirst();
        }
        return clicks.size();
    }
}
