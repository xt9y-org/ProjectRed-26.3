package dev.xt9y.projectred.integration;

import static org.junit.jupiter.api.Assertions.*;

import dev.xt9y.projectred.multipart.Part;
import org.junit.jupiter.api.Test;

final class GatePersistenceTest {
    @Test
    void counterConfigurationSurvivesEncodeDecode() {
        GatePart gate = new GatePart(GateType.COUNTER, 0, 3);
        gate.adjustCounterMax(37);
        gate.adjustCounterIncrement(5);
        gate.adjustCounterDecrement(3);
        gate.cycleShape();

        Part decoded = Part.decode(gate.encode());
        assertInstanceOf(GatePart.class, decoded);
        GatePart copy = (GatePart) decoded;

        assertEquals(GateType.COUNTER, copy.type());
        assertEquals(3, copy.rotation());
        assertEquals(gate.shape(), copy.shape());
        assertEquals(gate.counterMax(), copy.counterMax());
        assertEquals(gate.counterIncrement(), copy.counterIncrement());
        assertEquals(gate.counterDecrement(), copy.counterDecrement());
    }

    @Test
    void timerConfigurationSurvivesEncodeDecode() {
        GatePart gate = new GatePart(GateType.TIMER, 5, 1);
        gate.adjustTimer(160);

        GatePart copy = (GatePart) Part.decode(gate.encode());
        assertEquals(GateType.TIMER, copy.type());
        assertEquals(5, copy.slot());
        assertEquals(1, copy.rotation());
        assertEquals(gate.timerPeriod(), copy.timerPeriod());
    }

    @Test
    void segmentDisplayColourSurvivesEncodeDecode() {
        GatePart gate = new GatePart(GateType.SEGMENT_DISPLAY, 2, 0);
        gate.setDisplayColor(11);
        gate.cycleShape();

        GatePart copy = (GatePart) Part.decode(gate.encode());
        assertEquals(11, copy.state());
        assertEquals(1, copy.shape());
    }
}
