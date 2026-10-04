package dev.xt9y.projectred.multipart;

import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.transmission.WirePart;
import dev.xt9y.projectred.transmission.WireSpec;

public abstract class Part {
    public static final int CENTER_SLOT = 6;

    private final int slot;

    protected Part(int slot) {
        if (slot < 0 || slot > CENTER_SLOT) throw new IllegalArgumentException("invalid multipart slot");
        this.slot = slot;
    }

    public final int slot() {
        return slot;
    }

    public final boolean center() {
        return slot == CENTER_SLOT;
    }

    public abstract String encode();

    public String encode(long gameTime) {
        return encode();
    }

    public static Part decode(String encoded) {
        String[] p = encoded.split("\\|", -1);
        if (p.length < 3) throw new IllegalArgumentException("invalid multipart part");
        int slot = Integer.parseInt(p[2]);

        return switch (p[0]) {
            case "w" -> {
                WireSpec spec = WireSpec.byId(p[1]);
                if (spec == null) throw new IllegalArgumentException("unknown wire");
                yield WirePart.decode(spec, slot, p);
            }
            case "g" -> {
                GateType type = GateType.byId(p[1]);
                if (type == null) throw new IllegalArgumentException("unknown gate");
                yield GatePart.decode(type, slot, p);
            }
            default -> throw new IllegalArgumentException("unknown part type");
        };
    }
}
