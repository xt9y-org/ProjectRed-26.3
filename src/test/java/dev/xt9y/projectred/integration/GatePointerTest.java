package dev.xt9y.projectred.integration;

import static org.junit.jupiter.api.Assertions.*;

import dev.xt9y.projectred.multipart.Part;
import org.junit.jupiter.api.Test;

final class GatePointerTest {
    private static GatePart decodeWithPointer(
            GateType type,
            int shape,
            int timerPeriod,
            long pointerStart,
            int counterValue,
            int counterMax
    ) {
        String encoded = "g|" + type.id()
                + "|0|0|" + shape
                + "|0|0|-1|" + timerPeriod
                + "|" + pointerStart
                + "|" + counterValue
                + "|" + counterMax
                + "|1|1|0|0|0|0|0|0|65535|0";
        return (GatePart) Part.decode(encoded);
    }

    @Test
    void timerPointerUsesTheUpstreamPeriodMinusTwoSweep() {
        GatePart timer = decodeWithPointer(
                GateType.TIMER,
                0,
                40,
                100,
                0,
                10
        );

        assertEquals(
                0.0F,
                timer.pointerAngle(100, 0, 0),
                0.0001F
        );
        assertEquals(
                (float) Math.PI,
                timer.pointerAngle(119, 0, 0),
                0.0001F
        );
        assertEquals(
                (float) Math.PI * 2.0F,
                timer.pointerAngle(138, 0, 0),
                0.0001F
        );
    }

    @Test
    void sequencerPointerRunsOneTurnAcrossFourIntervals() {
        GatePart normal = decodeWithPointer(
                GateType.SEQUENCER,
                0,
                40,
                -1,
                0,
                10
        );
        GatePart reversed = decodeWithPointer(
                GateType.SEQUENCER,
                1,
                40,
                -1,
                0,
                10
        );

        float quarter = (float) Math.PI / 2.0F;
        assertEquals(quarter, normal.pointerAngle(0, 40, 0), 0.0001F);
        assertEquals(-quarter, reversed.pointerAngle(0, 40, 0), 0.0001F);
    }

    @Test
    void counterPointerUsesUpstreamGaugeRangeAndMirrorsPosition() {
        GatePart low = decodeWithPointer(
                GateType.COUNTER,
                0,
                40,
                -1,
                0,
                10
        );
        GatePart high = decodeWithPointer(
                GateType.COUNTER,
                0,
                40,
                -1,
                10,
                10
        );
        GatePart mirrored = decodeWithPointer(
                GateType.COUNTER,
                1,
                40,
                -1,
                5,
                10
        );

        assertEquals(
                (float) Math.toRadians(210),
                low.pointerAngle(0, 0, 0),
                0.0001F
        );
        assertEquals(
                (float) Math.toRadians(330),
                high.pointerAngle(0, 0, 0),
                0.0001F
        );
        assertEquals(3.0F / 16.0F, low.pointerLateralOffset(), 0.0001F);
        assertEquals(-3.0F / 16.0F, mirrored.pointerLateralOffset(), 0.0001F);
    }

    @Test
    void stateCellUsesUpstreamShortSweepAndReflectedPosition() {
        GatePart running = decodeWithPointer(
                GateType.STATE_CELL,
                0,
                40,
                100,
                0,
                10
        );
        GatePart reflected = decodeWithPointer(
                GateType.STATE_CELL,
                1,
                40,
                100,
                0,
                10
        );

        assertEquals(
                -(float) Math.PI / 2.0F,
                running.pointerAngle(100, 0, 0),
                0.0001F
        );
        assertEquals(
                0.5F - (float) Math.PI / 2.0F,
                running.pointerAngle(119, 0, 0),
                0.0001F
        );
        assertEquals(5.0F / 16.0F, running.pointerLateralOffset(), 0.0001F);
        assertEquals(-5.0F / 16.0F, reflected.pointerLateralOffset(), 0.0001F);
    }

    @Test
    void gatesWithoutPointersReturnNan() {
        assertTrue(
                Float.isNaN(
                        new GatePart(GateType.AND, 0, 0)
                                .pointerAngle(0, 0, 0)
                )
        );
    }
}
