package dev.xt9y.projectred.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GateTypeTest {
    @Test
    void containsExactlyModernIntegrationRedstoneGates() {
        assertEquals(34, GateType.values().length);

        Set<String> ids = new HashSet<>();
        for (GateType type : GateType.values()) {
            assertTrue(ids.add(type.id()), "duplicate gate id: " + type.id());
            assertSame(type, GateType.byId(type.id()));
        }

        assertTrue(ids.contains("or_gate"));
        assertTrue(ids.contains("timer_gate"));
        assertTrue(ids.contains("bus_transceiver_gate"));
        assertTrue(ids.contains("segment_display_gate"));
        assertFalse(ids.contains("ic_gate"));
    }

    @Test
    void unknownGateIdIsRejected() {
        assertNull(GateType.byId("ic_gate"));
        assertNull(GateType.byId("machine"));
    }
}
