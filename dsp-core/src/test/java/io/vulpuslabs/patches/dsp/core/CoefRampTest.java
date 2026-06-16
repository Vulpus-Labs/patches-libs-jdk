package io.vulpuslabs.patches.dsp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ported from {@code patches_dsp::coef_ramp::tests} (scalar cases). */
class CoefRampTest {

    /** Advance one sample, ignoring the active values. */
    private static void step(CoefRamp r) {
        r.advance(a -> null);
    }

    @Test
    void beginRampSnapsAndComputesDelta() {
        CoefRamp r = new CoefRamp(3, 8);
        // First ramp: active 0, target 0 → new target. Delta = (target - 0) / 8.
        r.beginRamp(t -> {
            t[0] = 1.0f;
            t[1] = 2.0f;
            t[2] = -1.0f;
        });
        // Active snaps to the previous target (0); the delta then shows up as the
        // first per-sample step away from it.
        assertEquals(0.0f, r.active()[0]);
        assertEquals(0.0f, r.active()[1]);
        assertEquals(0.0f, r.active()[2]);
        step(r);
        assertEquals(0.125f, r.active()[0], 1e-6f);
        assertEquals(0.25f, r.active()[1], 1e-6f);
        assertEquals(-0.125f, r.active()[2], 1e-6f);
    }

    @Test
    void advanceApproachesTarget() {
        CoefRamp r = new CoefRamp(2, 16);
        r.beginRamp(t -> {
            t[0] = 1.0f;
            t[1] = 2.0f;
        });
        for (int i = 0; i < 16; i++) {
            step(r);
        }
        assertEquals(1.0f, r.active()[0], 1e-4f);
        assertEquals(2.0f, r.active()[1], 1e-4f);
    }

    @Test
    void secondRampSnapsToPreviousTarget() {
        CoefRamp r = new CoefRamp(1, 8);
        r.beginRamp(t -> t[0] = 1.0f);
        for (int i = 0; i < 4; i++) {
            step(r);
        }
        float before = r.active()[0];
        assertTrue(before > 0.4f && before < 0.6f, "active should have drifted partway, got " + before);
        // New ramp: active must snap to previous target (1.0), not stay drifted.
        r.beginRamp(t -> t[0] = 2.0f);
        assertEquals(1.0f, r.active()[0]);
        // Delta = (2 - 1) / 8 = 0.125, observable on the next step.
        step(r);
        assertEquals(1.125f, r.active()[0], 1e-6f);
    }

    @Test
    void setStaticZeroesDelta() {
        CoefRamp r = new CoefRamp(2, 8);
        r.beginRamp(t -> {
            t[0] = 1.0f;
            t[1] = 2.0f;
        });
        r.setStatic(v -> {
            v[0] = 5.0f;
            v[1] = 6.0f;
        });
        assertEquals(5.0f, r.active()[0]);
        assertEquals(6.0f, r.active()[1]);
        // Deltas zeroed: advancing leaves the values put.
        step(r);
        assertEquals(5.0f, r.active()[0]);
        assertEquals(6.0f, r.active()[1]);
    }
}
