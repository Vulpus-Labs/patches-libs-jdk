package io.vulpuslabs.patches.dsp.filters.svf;

import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.makeKernel;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T3 — DC and Nyquist behaviour. */
class SvfDcNyquistTest {

    @Test
    void t3DcLowpassPasses() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.0f);
        for (int i = 0; i < 48_000; i++) {
            kernel.tick(1.0f);
        }
        assertTrue(Math.abs(kernel.lpOut - 1.0f) < 1e-3f,
                "LP DC output should be ≈1.0, got " + kernel.lpOut);
    }

    @Test
    void t3DcHighpassRejects() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.0f);
        for (int i = 0; i < 48_000; i++) {
            kernel.tick(1.0f);
        }
        assertTrue(Math.abs(kernel.hpOut) < 1e-3f,
                "HP DC output should be ≈0.0, got " + kernel.hpOut);
    }

    @Test
    void t3NyquistHighpassPasses() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.0f);
        float peak = 0.0f;
        for (int i = 0; i < 4096; i++) {
            float x = (i % 2 == 0) ? 1.0f : -1.0f;
            kernel.tick(x);
            if (i > 2048 && Math.abs(kernel.hpOut) > peak) {
                peak = Math.abs(kernel.hpOut);
            }
        }
        assertTrue(peak > 0.5f, "HP Nyquist amplitude should be >0.5, got " + peak);
    }
}
