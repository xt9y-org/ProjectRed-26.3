package dev.xt9y.projectred.transmission;

import java.util.function.Supplier;

/**
 * ProjectRed wires must not observe their own vanilla-redstone power while
 * calculating a new signal. Direct wire-to-wire propagation is still active;
 * only the block-level weak/direct power hooks are suppressed.
 */
public final class RedwirePowerContext {
    private static final ThreadLocal<Integer> DEPTH =
            ThreadLocal.withInitial(() -> 0);

    public static boolean suppressed() {
        return DEPTH.get() > 0;
    }

    public static <T> T suppress(Supplier<T> action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int next = DEPTH.get() - 1;
            if (next == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(next);
            }
        }
    }

    private RedwirePowerContext() {}
}
