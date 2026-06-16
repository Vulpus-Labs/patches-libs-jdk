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
        SvfKernel kernel = new SvfKernel(SvfCoeffs.of(1_000.0f, SAMPLE_RATE, qNorm));
        for (int i = 0; i < 10_000; i++) {
            FilterOutputs o = kernel.tick(i == 0 ? 1.0f : 0.0f);
            assertTrue(Math.abs(o.lp()) < 100.0f && Math.abs(o.hp()) < 100.0f && Math.abs(o.bp()) < 100.0f,
                    "sample " + i + ": unbounded lp=" + o.lp() + " hp=" + o.hp() + " bp=" + o.bp());
        }
    }

    @Test
    void t5StabilityAdsrFmSweepHighQ() {
        float qNorm = 0.95f;
        float baseCutoffVoct = 6.0f;
        float c0 = 16.351_599f;
        float baseFc = DspMath.clamp((float) (c0 * Math.pow(2.0, baseCutoffVoct)), 1.0f, SAMPLE_RATE * 0.499f);
        int interval = 32;
        SvfKernel kernel = new SvfKernel(SvfCoeffs.of(baseFc, SAMPLE_RATE, qNorm), interval);

        int total = 10_000;
        int attackSamples = 1536;

        for (int n = 0; n < total; n++) {
            if (n % interval == 0) {
                float env = (n < attackSamples) ? (float) n / attackSamples : 1.0f;
                float fc = DspMath.clamp(
                        (float) (c0 * Math.pow(2.0, baseCutoffVoct + env * 4.0f)),
                        1.0f, SAMPLE_RATE * 0.499f);
                kernel.beginRamp(SvfCoeffs.of(fc, SAMPLE_RATE, qNorm));
            }
            float x = (n < 64) ? 0.5f : 0.0f;
            FilterOutputs o = kernel.tick(x);
            assertTrue(Float.isFinite(o.lp()) && Float.isFinite(o.hp()) && Float.isFinite(o.bp()),
                    "sample " + n + ": NaN/Inf");
            assertTrue(Math.abs(o.lp()) < 1e6f && Math.abs(o.hp()) < 1e6f && Math.abs(o.bp()) < 1e6f,
                    "sample " + n + ": runaway");
        }
    }

    @Test
    void stateResetZeroesOutputs() {
        SvfCoeffs c = SvfCoeffs.of(1_000.0f, SAMPLE_RATE, 0.5f);
        float f = c.f();
        SvfKernel kernel = new SvfKernel(c);
        float lastLp = 0.0f;
        for (int i = 0; i < 100; i++) {
            lastLp = kernel.tick(0.5f).lp();
        }
        // State accumulated under DC drive, so the output is non-zero.
        assertNotEquals(0.0f, lastLp);
        kernel.resetState();

        // After reset: lp = 0 + f*0 = 0; hp = x - 0 - d*0 = x; bp = 0 + f*x.
        float x = 1.0f;
        FilterOutputs o = kernel.tick(x);
        assertEquals(0.0f, o.lp(), 1e-9f);
        assertEquals(x, o.hp(), 1e-9f);
        assertEquals(f * x, o.bp(), 1e-9f);
    }
}
