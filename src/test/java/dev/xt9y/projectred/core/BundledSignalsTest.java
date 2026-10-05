package dev.xt9y.projectred.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class BundledSignalsTest {
    @Test
    void digitalPackRoundTripsAllChannels() {
        int mask = 0xA55A;
        assertEquals(mask, BundledSignals.packDigital(BundledSignals.unpackDigital(mask)));
    }

    @Test
    void raiseUsesMaximumAndOptionalSingleStepAttenuation() {
        int[] destination = new int[16];
        destination[3] = 80;

        int[] source = new int[16];
        source[3] = 60;
        source[7] = 255;

        int[] out = BundledSignals.raise(destination, source, true);
        assertSame(destination, out);
        assertEquals(80, out[3]);
        assertEquals(254, out[7]);

        int[] unattenuated = BundledSignals.raise(new int[16], source, false);
        assertEquals(255, unattenuated[7]);
    }

    @Test
    void nullSignalBehavesAsZero() {
        assertTrue(BundledSignals.isZero(null));
        assertTrue(BundledSignals.equal(null, new int[16]));
        assertNull(BundledSignals.copy(null));
    }
}
