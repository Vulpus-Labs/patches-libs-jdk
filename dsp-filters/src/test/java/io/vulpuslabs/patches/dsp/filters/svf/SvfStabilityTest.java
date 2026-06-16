package io.vulpuslabs.patches.dsp.filters.svf;

import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.SAMPLE_RATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vulpuslabs.patches.dsp.core.DspMath;
import org.junit.jupiter.api.Test;

/** T4 / T5 — stability under high resonance and modulation, plus state reset. */
class SvfStabilityTest {

    @Test
    void t4StabilityHighResonance() {
        float qNorm = 0.83f; // damping ≈ 0.1, Q ≈ 10
        float f = Svf.svfF(1_000.0f, SAMPLE_RATE);
        float d = Svf.qToDamp(qNorm);
        SvfKernel kernel = new SvfKernel(f, d);
        for (int i = 0; i < 10_000; i++) {
            kernel.tick(i == 0 ? 1.0f : 0.0f);
            assertTrue(Math.abs(kernel.lpOut) < 100.0f
                            && Math.abs(kernel.hpOut) < 100.0f
                            && Math.abs(kernel.bpOut) < 100.0f,
                    "sample " + i + ": unbounded lp=" + kernel.lpOut + " hp=" + kernel.hpOut + " bp=" + kernel.bpOut);
        }
    }

    @Test
    void t5StabilityAdsrFmSweepHighQ() {
        float qNorm = 0.95f;
        float baseCutoffVoct = 6.0f;
        float c0 = 16.351_599f;
        float d = Svf.qToDamp(qNorm);
        float baseFc = DspMath.clamp((float) (c0 * Math.pow(2.0, baseCutoffVoct)), 1.0f, SAMPLE_RATE * 0.499f);
        int interval = 32;
        SvfKernel kernel = new SvfKernel(Svf.svfF(baseFc, SAMPLE_RATE), d, interval);

        int total = 10_000;
        int attackSamples = 1536;

        for (int n = 0; n < total; n++) {
            if (n % interval == 0) {
                float env = (n < attackSamples) ? (float) n / attackSamples : 1.0f;
                float fc = DspMath.clamp(
                        (float) (c0 * Math.pow(2.0, baseCutoffVoct + env * 4.0f)),
                        1.0f, SAMPLE_RATE * 0.499f);
                kernel.beginRamp(Svf.svfF(fc, SAMPLE_RATE), d);
            }
            float x = (n < 64) ? 0.5f : 0.0f;
            kernel.tick(x);
            assertTrue(Float.isFinite(kernel.lpOut) && Float.isFinite(kernel.hpOut) && Float.isFinite(kernel.bpOut),
                    "sample " + n + ": NaN/Inf");
            assertTrue(Math.abs(kernel.lpOut) < 1e6f && Math.abs(kernel.hpOut) < 1e6f && Math.abs(kernel.bpOut) < 1e6f,
                    "sample " + n + ": runaway");
        }
    }

    @Test
    void stateResetZeroesOutputs() {
        float f = Svf.svfF(1_000.0f, SAMPLE_RATE);
        float d = Svf.qToDamp(0.5f);
        SvfKernel kernel = new SvfKernel(f, d);
        for (int i = 0; i < 100; i++) {
            kernel.tick(0.5f);
        }
        assertNotEquals(0.0f, kernel.lpState);
        kernel.resetState();

        // After reset: lp = 0 + f*0 = 0; hp = x - 0 - d*0 = x; bp = 0 + f*x.
        float x = 1.0f;
        kernel.tick(x);
        assertEquals(0.0f, kernel.lpOut, 1e-9f);
        assertEquals(x, kernel.hpOut, 1e-9f);
        assertEquals(f * x, kernel.bpOut, 1e-9f);
    }
}
