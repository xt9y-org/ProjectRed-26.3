package dev.xt9y.projectred.transmission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.DyeColor;

public record WireSpec(String id, WireFamily family, int color, boolean framed) {
    private static final List<WireSpec> ALL = buildAll();

    public static List<WireSpec> all() {
        return ALL;
    }

    public static WireSpec byId(String id) {
        for (WireSpec spec : ALL) if (spec.id.equals(id)) return spec;
        return null;
    }

    public boolean bundledCompatible(WireSpec other) {
        if (other == null || family != WireFamily.BUNDLED || other.family != WireFamily.BUNDLED) return false;
        return color < 0 || other.color < 0 || color == other.color;
    }

    public boolean redwireCompatible(WireSpec other) {
        if (other == null || family == WireFamily.BUNDLED || other.family == WireFamily.BUNDLED) return false;
        if (family == WireFamily.INSULATED && other.family == WireFamily.INSULATED) return color == other.color;
        return true;
    }

    private static List<WireSpec> buildAll() {
        List<WireSpec> specs = new ArrayList<>();
        specs.add(new WireSpec("red_alloy_wire", WireFamily.RED_ALLOY, -1, false));
        for (DyeColor c : DyeColor.values()) {
            specs.add(new WireSpec(c.getName() + "_insulated_wire", WireFamily.INSULATED, c.getId(), false));
        }
        specs.add(new WireSpec("bundled_cable", WireFamily.BUNDLED, -1, false));
        for (DyeColor c : DyeColor.values()) {
            specs.add(new WireSpec(c.getName() + "_bundled_cable", WireFamily.BUNDLED, c.getId(), false));
        }

        specs.add(new WireSpec("framed_red_alloy_wire", WireFamily.RED_ALLOY, -1, true));
        for (DyeColor c : DyeColor.values()) {
            specs.add(new WireSpec("framed_" + c.getName() + "_insulated_wire", WireFamily.INSULATED, c.getId(), true));
        }
        specs.add(new WireSpec("framed_bundled_cable", WireFamily.BUNDLED, -1, true));
        for (DyeColor c : DyeColor.values()) {
            specs.add(new WireSpec("framed_" + c.getName() + "_bundled_cable", WireFamily.BUNDLED, c.getId(), true));
        }
        return Collections.unmodifiableList(specs);
    }
}
