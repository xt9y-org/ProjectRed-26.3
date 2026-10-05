package dev.xt9y.projectred.transmission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WireSpecTest {
    @Test
    void registersExactlyTheRedstoneTransmissionSet() {
        assertEquals(68, WireSpec.all().size());

        Set<String> ids = new HashSet<>();
        for (WireSpec spec : WireSpec.all()) {
            assertTrue(ids.add(spec.id()), "duplicate wire id: " + spec.id());
            assertNotNull(spec.family());
            assertFalse(spec.id().contains("power"));
            assertFalse(spec.id().contains("low_load"));
        }

        assertTrue(ids.contains("red_alloy_wire"));
        assertTrue(ids.contains("framed_red_alloy_wire"));
        assertTrue(ids.contains("neutral_bundled_wire"));
        assertTrue(ids.contains("neutral_framed_bundled_wire"));
        assertTrue(ids.contains("white_insulated_wire"));
        assertTrue(ids.contains("black_framed_bundled_wire"));
    }

    @Test
    void insulatedColoursOnlyConnectToSameInsulatedColour() {
        WireSpec red = WireSpec.byId("red_insulated_wire");
        WireSpec blue = WireSpec.byId("blue_insulated_wire");
        WireSpec alloy = WireSpec.byId("red_alloy_wire");

        assertNotNull(red);
        assertNotNull(blue);
        assertNotNull(alloy);

        assertTrue(red.redwireCompatible(red));
        assertFalse(red.redwireCompatible(blue));
        assertTrue(red.redwireCompatible(alloy));
        assertTrue(alloy.redwireCompatible(red));
    }

    @Test
    void neutralBundledConnectsToEveryBundledColour() {
        WireSpec neutral = WireSpec.byId("neutral_bundled_wire");
        WireSpec red = WireSpec.byId("red_bundled_wire");
        WireSpec blue = WireSpec.byId("blue_bundled_wire");

        assertNotNull(neutral);
        assertNotNull(red);
        assertNotNull(blue);

        assertTrue(neutral.bundledCompatible(red));
        assertTrue(neutral.bundledCompatible(blue));
        assertFalse(red.bundledCompatible(blue));
        assertTrue(red.bundledCompatible(red));
    }
}
