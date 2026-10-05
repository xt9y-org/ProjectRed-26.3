package dev.xt9y.projectred.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StatefulGateDefaultsTest {
    @Test
    void upstreamInitialStatesArePreserved() {
        assertEquals(0x30, new GatePart(GateType.SR_LATCH, 0, 0).state());
        assertEquals(0x10, new GatePart(GateType.TOGGLE_LATCH, 0, 0).state());
        assertEquals(14, new GatePart(GateType.SEGMENT_DISPLAY, 0, 0).state());
        assertEquals(0, new GatePart(GateType.OR, 0, 0).state());
    }

    @Test
    void repeaterCyclesAllNineProjectRedDelays() {
        GatePart gate = new GatePart(GateType.REPEATER, 0, 0);
        int[] expected = {2, 4, 6, 8, 16, 32, 64, 128, 256};

        for (int delay : expected) {
            assertEquals(delay, gate.delay());
            gate.cycleShape();
        }

        assertEquals(2, gate.delay());
        assertEquals(0, gate.shape());
    }

    @Test
    void binaryShapeGatesToggleAndReturn() {
        for (GateType type : new GateType[] {
                GateType.TRANSPARENT_LATCH,
                GateType.SEQUENCER,
                GateType.COUNTER,
                GateType.STATE_CELL,
                GateType.COMPARATOR,
                GateType.BUS_TRANSCEIVER,
                GateType.BUS_RANDOMIZER,
                GateType.BUS_CONVERTER,
                GateType.SEGMENT_DISPLAY
        }) {
            GatePart gate = new GatePart(type, 0, 0);
            assertEquals(0, gate.shape(), type.name());
            gate.cycleShape();
            assertEquals(1, gate.shape(), type.name());
            gate.cycleShape();
            assertEquals(0, gate.shape(), type.name());
        }
    }

    @Test
    void timerAndCounterConfigurationClampToProjectRedRanges() {
        GatePart timer = new GatePart(GateType.TIMER, 0, 0);
        timer.adjustTimer(-100000);
        assertEquals(4, timer.timerPeriod());
        timer.adjustTimer(Integer.MAX_VALUE);
        assertEquals(20 * 60 * 60, timer.timerPeriod());

        GatePart counter = new GatePart(GateType.COUNTER, 0, 0);
        counter.adjustCounterMax(-100000);
        assertEquals(1, counter.counterMax());
        counter.adjustCounterMax(Integer.MAX_VALUE);
        assertEquals(32767, counter.counterMax());
    }

    @Test
    void segmentDisplayRejectsOutOfRangeColours() {
        GatePart display = new GatePart(GateType.SEGMENT_DISPLAY, 0, 0);
        display.setDisplayColor(-10);
        assertEquals(0, display.state());
        display.setDisplayColor(99);
        assertEquals(15, display.state());
    }
}
