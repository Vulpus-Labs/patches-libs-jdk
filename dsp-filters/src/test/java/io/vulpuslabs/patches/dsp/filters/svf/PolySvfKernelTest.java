package io.vulpuslabs.patches.dsp.filters.svf;

import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.SAMPLE_RATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Poly kernel: parity with the mono kernel, voice independence, reset, determinism. */
class PolySvfKernelTest {

    private static final int VOICES = 16;

    @Test
    void polyMatchesMonoWhenAllVoicesIdentical() {
        SvfCoeffs c = SvfCoeffs.of(1_000.0f, SAMPLE_RATE, 0.5f);
        SvfKernel mono = new SvfKernel(c);
        PolySvfKernel poly = new PolySvfKernel(fill(c));

        float[] x = new float[VOICES];
        PolyFilterOutputs out = newOutputs();

        for (int i = 0; i < 4096; i++) {
            float in = (float) Math.sin(2.0 * Math.PI * 220.0 / SAMPLE_RATE * i);
            java.util.Arrays.fill(x, in);
            FilterOutputs m = mono.tick(in);
            poly.tickAll(x, out);
            for (int v = 0; v < VOICES; v++) {
                assertEquals(m.lp(), out.lp()[v], 1e-9f, "lp voice " + v + " @ " + i);
                assertEquals(m.hp(), out.hp()[v], 1e-9f, "hp voice " + v + " @ " + i);
                assertEquals(m.bp(), out.bp()[v], 1e-9f, "bp voice " + v + " @ " + i);
            }
        }
    }

    @Test
    void voicesAreIndependent() {
        SvfCoeffs[] coeffs = new SvfCoeffs[VOICES];
        for (int v = 0; v < VOICES; v++) {
            coeffs[v] = SvfCoeffs.of(200.0f + 300.0f * v, SAMPLE_RATE, 0.5f);
        }
        PolySvfKernel poly = new PolySvfKernel(coeffs);

        float[] x = new float[VOICES];
        PolyFilterOutputs out = newOutputs();
        for (int i = 0; i < 256; i++) {
            java.util.Arrays.fill(x, i == 0 ? 1.0f : 0.0f);
            poly.tickAll(x, out);
        }
        // Different cutoffs → different lowpass state by now.
        assertNotEquals(out.lp()[0], out.lp()[VOICES - 1], "voices at different cutoffs should diverge");
    }

    @Test
    void resetZeroesAllVoices() {
        PolySvfKernel poly = new PolySvfKernel(fill(SvfCoeffs.of(1_000.0f, SAMPLE_RATE, 0.5f)));
        float[] x = new float[VOICES];
        PolyFilterOutputs out = newOutputs();
        for (int i = 0; i < 100; i++) {
            java.util.Arrays.fill(x, 0.5f);
            poly.tickAll(x, out);
        }
        poly.resetState();

        // After reset, an impulse-free input gives lp = 0, hp = x, bp = f*x for every voice.
        float f = SvfCoeffs.of(1_000.0f, SAMPLE_RATE, 0.5f).f();
        java.util.Arrays.fill(x, 1.0f);
        poly.tickAll(x, out);
        for (int v = 0; v < VOICES; v++) {
            assertEquals(0.0f, out.lp()[v], 1e-9f, "lp voice " + v);
            assertEquals(1.0f, out.hp()[v], 1e-9f, "hp voice " + v);
            assertEquals(f, out.bp()[v], 1e-9f, "bp voice " + v);
        }
    }

    @Test
    void deterministic() {
        float[] a = runBlock();
        float[] b = runBlock();
        for (int i = 0; i < a.length; i++) {
            assertEquals(Float.floatToIntBits(a[i]), Float.floatToIntBits(b[i]), "non-deterministic at " + i);
        }
    }

    @Test
    void polyStaysFiniteUnderRampedHighQSweep() {
        SvfCoeffs[] base = new SvfCoeffs[VOICES];
        for (int v = 0; v < VOICES; v++) {
            base[v] = SvfCoeffs.of(500.0f, SAMPLE_RATE, 0.95f);
        }
        int interval = 32;
        PolySvfKernel poly = new PolySvfKernel(base, interval);
        float[] x = new float[VOICES];
        PolyFilterOutputs out = newOutputs();

        for (int n = 0; n < 10_000; n++) {
            if (n % interval == 0) {
                SvfCoeffs[] tgt = new SvfCoeffs[VOICES];
                float env = Math.min(1.0f, n / 4000.0f);
                for (int v = 0; v < VOICES; v++) {
                    tgt[v] = SvfCoeffs.of(500.0f + env * 8_000.0f, SAMPLE_RATE, 0.95f);
                }
                poly.beginRamp(tgt);
            }
            java.util.Arrays.fill(x, n < 64 ? 0.5f : 0.0f);
            poly.tickAll(x, out);
            for (int v = 0; v < VOICES; v++) {
                assertTrue(Float.isFinite(out.lp()[v]) && Float.isFinite(out.hp()[v]) && Float.isFinite(out.bp()[v]),
                        "voice " + v + " @ " + n + ": NaN/Inf");
            }
        }
    }

    private static float[] runBlock() {
        PolySvfKernel poly = new PolySvfKernel(fill(SvfCoeffs.of(800.0f, SAMPLE_RATE, 0.4f)));
        float[] x = new float[VOICES];
        PolyFilterOutputs out = newOutputs();
        float[] result = new float[256];
        for (int i = 0; i < result.length; i++) {
            java.util.Arrays.fill(x, (float) Math.sin(2.0 * Math.PI * 440.0 / SAMPLE_RATE * i));
            poly.tickAll(x, out);
            result[i] = out.lp()[0];
        }
        return result;
    }

    private static PolyFilterOutputs newOutputs() {
        return PolyFilterOutputs.create(VOICES);
    }

    private static SvfCoeffs[] fill(SvfCoeffs c) {
        SvfCoeffs[] a = new SvfCoeffs[VOICES];
        java.util.Arrays.fill(a, c);
        return a;
    }
}
