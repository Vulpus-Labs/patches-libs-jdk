package io.vulpuslabs.patches.dsp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ported from {@code patches_dsp::coef_ramp::tests} (scalar cases). */
class CoefRampTest {

    @Test
    void beginRampSnapsAndComputesDelta() {
        CoefRamp r = new CoefRamp(3, 8);
        // First ramp: active 0, target 0 → new target. Delta = (target - 0) / 8.
        r.beginRamp(t -> {
            t[0] = 1.0f;
            t[1] = 2.0f;
            t[2] = -1.0f;
        });
        assertEquals(0.0f, r.active()[0]);
        assertEquals(0.0f, r.active()[1]);
        assertEquals(0.0f, r.active()[2]);
        assertEquals(1.0f, r.targets()[0]);
        assertEquals(2.0f, r.targets()[1]);
        assertEquals(-1.0f, r.targets()[2]);
        assertEquals(0.125f, r.deltas()[0], 1e-6f);
        assertEquals(0.25f, r.deltas()[1], 1e-6f);
        assertEquals(-0.125f, r.deltas()[2], 1e-6f);
    }

    @Test
    void advanceApproachesTarget() {
        CoefRamp r = new CoefRamp(2, 16);
        r.beginRamp(t -> {
            t[0] = 1.0f;
            t[1] = 2.0f;
        });
        for (int i = 0; i < 16; i++) {
            r.advance();
        }
        assertEquals(1.0f, r.active()[0], 1e-4f);
        assertEquals(2.0f, r.active()[1], 1e-4f);
    }

    @Test
    void secondRampSnapsToPreviousTarget() {
        CoefRamp r = new CoefRamp(1, 8);
        r.beginRamp(t -> t[0] = 1.0f);
        for (int i = 0; i < 4; i++) {
            r.advance();
        }
        float before = r.active()[0];
        assertTrue(before > 0.4f && before < 0.6f, "active should have drifted partway, got " + before);
        // New ramp: active must snap to previous target (1.0), not stay drifted.
        r.beginRamp(t -> t[0] = 2.0f);
        assertEquals(1.0f, r.active()[0]);
        assertEquals(2.0f, r.targets()[0]);
        assertEquals(0.125f, r.deltas()[0], 1e-6f);
    }

    @Test
    void setStaticZeroesDelta() {
        CoefRamp r = new CoefRamp(2, 8);
        r.beginRamp(t -> {
            t[0] = 1.0f;
            t[1] = 2.0f;
        });
        assertNotEquals(0.0f, r.deltas()[0]);
        r.setStatic(v -> {
            v[0] = 5.0f;
            v[1] = 6.0f;
        });
        assertEquals(5.0f, r.active()[0]);
        assertEquals(6.0f, r.active()[1]);
        assertEquals(0.0f, r.deltas()[0]);
        assertEquals(0.0f, r.deltas()[1]);
    }
}
