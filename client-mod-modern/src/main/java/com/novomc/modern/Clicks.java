package com.novomc.modern;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.Deque;

/** Mouse clicks in the last second, for CPS and Keystrokes (fed by MouseHandlerMixin). */
public final class Clicks {
    private static final Deque<Long> LEFT = new ArrayDeque<Long>();
    private static final Deque<Long> RIGHT = new ArrayDeque<Long>();

    private Clicks() {}

    public static void press(int button) {
        // 26.3 numbers mouse buttons the SDL way: left 1, right 3.
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            LEFT.addLast(System.currentTimeMillis());
        } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            RIGHT.addLast(System.currentTimeMillis());
        }
    }

    public static int left() {
        return count(LEFT);
    }

    public static int right() {
        return count(RIGHT);
    }

    private static int count(Deque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.pollFirst();
        }
        return clicks.size();
    }
}
