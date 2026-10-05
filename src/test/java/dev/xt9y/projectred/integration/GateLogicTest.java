package dev.xt9y.projectred.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class GateLogicTest {
    private static GatePart gate(GateType type) {
        return new GatePart(type, 0, 0);
    }

    @Test
    void orAndNorTruthTablesMatchProjectRed() {
        GatePart or = gate(GateType.OR);
        GatePart nor = gate(GateType.NOR);

        assertEquals(0, or.calcOutput(0));
        assertEquals(1, nor.calcOutput(0));

        for (int input : new int[] {2, 4, 8, 6, 10, 12, 14}) {
            assertEquals(1, or.calcOutput(input));
            assertEquals(0, nor.calcOutput(input));
        }
    }

    @Test
    void andAndNandRequireEveryEnabledInput() {
        GatePart and = gate(GateType.AND);
        GatePart nand = gate(GateType.NAND);
        int mask = and.inputMask();

        assertEquals(0xE, mask);
        assertEquals(1, and.calcOutput(mask));
        assertEquals(0, nand.calcOutput(mask));

        for (int input : new int[] {0, 2, 4, 8, 6, 10, 12}) {
            assertEquals(0, and.calcOutput(input));
            assertEquals(1, nand.calcOutput(input));
        }
    }

    @Test
    void xorAndXnorUseTheTwoSideInputs() {
        GatePart xor = gate(GateType.XOR);
        GatePart xnor = gate(GateType.XNOR);

        assertEquals(0, xor.calcOutput(0));
        assertEquals(1, xnor.calcOutput(0));

        assertEquals(1, xor.calcOutput(2));
        assertEquals(0, xnor.calcOutput(2));

        assertEquals(1, xor.calcOutput(8));
        assertEquals(0, xnor.calcOutput(8));

        assertEquals(0, xor.calcOutput(10));
        assertEquals(1, xnor.calcOutput(10));
    }

    @Test
    void notAndBufferExposeThreeOutputsByDefault() {
        GatePart not = gate(GateType.NOT);
        GatePart buffer = gate(GateType.BUFFER);

        assertEquals(0xB, not.outputMask());
        assertEquals(0xB, buffer.outputMask());

        assertEquals(0xB, not.calcOutput(0));
        assertEquals(0, not.calcOutput(4));

        assertEquals(0, buffer.calcOutput(0));
        assertEquals(0xB, buffer.calcOutput(4));
    }

    @Test
    void multiplexerSelectsLeftOrRightWithCenterInput() {
        GatePart mux = gate(GateType.MULTIPLEXER);

        // Selector (local 2) low: local 1 is routed.
        assertEquals(0, mux.calcOutput(0));
        assertEquals(1, mux.calcOutput(2));
        assertEquals(0, mux.calcOutput(8));

        // Selector high: local 3 is routed.
        assertEquals(0, mux.calcOutput(4));
        assertEquals(0, mux.calcOutput(6));
        assertEquals(1, mux.calcOutput(12));
        assertEquals(1, mux.calcOutput(14));
    }

    @Test
    void transparentLatchTracksWhileEnabledAndHoldsWhileDisabled() {
        GatePart latch = gate(GateType.TRANSPARENT_LATCH);

        // Enable input is local 2. With it high, side input is copied.
        assertEquals(0, latch.calcOutput(4));
        assertEquals(0xF, latch.calcOutput(6));
        assertEquals(0xF, latch.calcOutput(12));

        // With enable low the gate returns its current output. Fresh state is low.
        assertEquals(0, latch.calcOutput(0));
        assertEquals(0, latch.calcOutput(2));
        assertEquals(0, latch.calcOutput(8));
    }

    @Test
    void defaultMasksMatchModernIntegrationLayout() {
        assertEquals(0xE, gate(GateType.OR).inputMask());
        assertEquals(0xA, gate(GateType.XOR).inputMask());
        assertEquals(0xE, gate(GateType.MULTIPLEXER).inputMask());
        assertEquals(0xA, gate(GateType.TOGGLE_LATCH).inputMask());
        assertEquals(0xE, gate(GateType.SYNCHRONIZER).inputMask());

        assertEquals(1, gate(GateType.OR).outputMask());
        assertEquals(5, gate(GateType.TOGGLE_LATCH).outputMask());
        assertEquals(0xB, gate(GateType.TIMER).outputMask());
        assertEquals(0xF, gate(GateType.SEQUENCER).outputMask());
    }
}
