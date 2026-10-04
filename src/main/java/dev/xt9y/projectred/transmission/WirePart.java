package dev.xt9y.projectred.transmission;

import dev.xt9y.projectred.core.BundledSignals;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import java.util.Arrays;
import net.minecraft.core.Direction;

public final class WirePart extends Part {
    private final WireSpec spec;
    private int signal;
    private final int[] bundled = new int[16];

    public WirePart(WireSpec spec, int slot) {
        super(slot);
        this.spec = spec;
    }

    public WireSpec spec() {
        return spec;
    }

    public int signal() {
        return signal;
    }

    public int[] bundled() {
        return bundled.clone();
    }

    public boolean recompute(MultipartBlockEntity owner) {
        if (spec.family() == WireFamily.BUNDLED) {
            int[] next = owner.calculateBundledInput(this);
            if (next == null) next = new int[16];
            if (Arrays.equals(next, bundled)) return false;
            System.arraycopy(next, 0, bundled, 0, 16);
            return true;
        }

        int next = owner.calculateRedwireInput(this);
        next = Math.max(0, Math.min(255, next));
        if (next == signal) return false;
        signal = next;
        return true;
    }

    public int vanillaSignal() {
        return (signal + 16) / 17;
    }

    public int[] bundledSignal() {
        if (spec.family() == WireFamily.BUNDLED) return bundled();
        if (spec.family() == WireFamily.INSULATED) {
            int[] out = new int[16];
            out[spec.color()] = signal;
            return out;
        }
        return null;
    }

    public boolean canConnect(WirePart other) {
        if (other == null) return false;
        if (spec.family() == WireFamily.BUNDLED) {
            return other.spec.family() == WireFamily.BUNDLED
                    ? spec.bundledCompatible(other.spec)
                    : other.spec.family() == WireFamily.INSULATED;
        }
        if (other.spec.family() == WireFamily.BUNDLED) return spec.family() == WireFamily.INSULATED;
        return spec.redwireCompatible(other.spec);
    }

    @Override
    public String encode() {
        StringBuilder b = new StringBuilder("w|").append(spec.id()).append('|').append(slot()).append('|').append(signal).append('|');
        for (int i = 0; i < 16; i++) {
            if (i != 0) b.append(',');
            b.append(bundled[i]);
        }
        return b.toString();
    }

    public static WirePart decode(WireSpec spec, int slot, String[] p) {
        WirePart part = new WirePart(spec, slot);
        if (p.length > 3) part.signal = Integer.parseInt(p[3]);
        if (p.length > 4 && !p[4].isEmpty()) {
            String[] values = p[4].split(",");
            for (int i = 0; i < Math.min(16, values.length); i++) part.bundled[i] = Integer.parseInt(values[i]);
        }
        return part;
    }
}
