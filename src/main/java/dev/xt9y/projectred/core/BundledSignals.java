package dev.xt9y.projectred.core;

import java.util.Arrays;

public final class BundledSignals {
    public static boolean isZero(int[] signal) {
        if (signal == null) return true;
        for (int v : signal) if (v != 0) return false;
        return true;
    }

    public static boolean equal(int[] a, int[] b) {
        if (a == null) return isZero(b);
        if (b == null) return isZero(a);
        return Arrays.equals(a, b);
    }

    public static int[] copy(int[] signal) {
        return signal == null ? null : signal.clone();
    }

    public static int[] raise(int[] signal, int[] source, boolean attenuate) {
        if (signal == null) signal = new int[16];
        if (source == null) return signal;
        for (int i = 0; i < 16; i++) {
            int value = Math.max(0, Math.min(255, source[i]) - (attenuate ? 1 : 0));
            signal[i] = Math.max(signal[i], value);
        }
        return signal;
    }

    public static int packDigital(int[] signal) {
        if (signal == null) return 0;
        int packed = 0;
        for (int i = 0; i < 16; i++) if (signal[i] != 0) packed |= 1 << i;
        return packed;
    }

    public static int[] unpackDigital(int packed) {
        int[] signal = new int[16];
        for (int i = 0; i < 16; i++) signal[i] = (packed & (1 << i)) != 0 ? 255 : 0;
        return signal;
    }

    private BundledSignals() {}
}
