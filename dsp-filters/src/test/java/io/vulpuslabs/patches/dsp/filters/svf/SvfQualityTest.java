package io.vulpuslabs.patches.dsp.filters.svf;

import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.SAMPLE_RATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T6 / T7 — SNR against an f64 reference and determinism, plus the coeffs API. */
class SvfQualityTest {

    @Test
    void t6SnrSvfLpVsF64Reference() {
        final float sr = 48_000.0f;
        final double sr64 = 48_000.0;
        final float fc = 1_000.0f;
        final float qNorm = 0.5f;
        final double driveHz = 200.0;
        final int n = 10_000;

        SvfKernel kernel = new SvfKernel(Svf.svfF(fc, sr), Svf.qToDamp(qNorm));

        // f64 reference coefficients — same formulas at double precision.
        double f64 = 2.0 * Math.sin(Math.PI * fc / sr64);
        double d64 = 2.0 * Math.pow(0.005, qNorm);

        double refLp = 0.0;
        double refBp = 0.0;
        double sumSqSignal = 0.0;
        double sumSqError = 0.0;

        for (int k = 0; k < n; k++) {
            double x64 = Math.sin(2.0 * Math.PI * driveHz / sr64 * k);
            float x32 = (float) x64;

            double lpNew = refLp + f64 * refBp;
            double hpNew = x64 - lpNew - d64 * refBp;
            double bpNew = refBp + f64 * hpNew;
            refLp = lpNew;
            refBp = bpNew;

            kernel.tick(x32);
            sumSqSignal += refLp * refLp;
            double err = kernel.lpOut - refLp;
            sumSqError += err * err;
        }

        double rmsSignal = Math.sqrt(sumSqSignal / n);
        double rmsError = Math.sqrt(sumSqError / n);
        double snrDb = 20.0 * Math.log10(rmsSignal / rmsError);
        assertTrue(snrDb >= 120.0, "SNR too low: " + snrDb + " dB (expected ≥ 120)");
    }

    @Test
    void t7Determinism() {
        float f = Svf.svfF(800.0f, SAMPLE_RATE);
        float d = Svf.qToDamp(0.4f);
        float[] input = new float[256];
        for (int i = 0; i < input.length; i++) {
            input[i] = (float) Math.sin(2.0 * Math.PI * 440.0 / SAMPLE_RATE * i);
        }

        float[] a = run(new SvfKernel(f, d), input);
        float[] b = run(new SvfKernel(f, d), input);
        for (int i = 0; i < input.length; i++) {
            assertEquals(Float.floatToIntBits(a[i]), Float.floatToIntBits(b[i]),
                    "non-deterministic at sample " + i);
        }
    }

    @Test
    void coeffsRoundTrip() {
        SvfCoeffs c = SvfCoeffs.of(440.0f, SAMPLE_RATE, 0.5f);
        SvfKernel k = SvfKernel.fromCoeffs(c);
        k.tick(1.0f); // just exercises the path
        assertEquals(c.f(), k.coefs.active()[0], 1e-9f);
        assertEquals(c.qDamp(), k.coefs.active()[1], 1e-9f);
    }

    private static float[] run(SvfKernel k, float[] input) {
        float[] out = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            k.tick(input[i]);
            out[i] = k.lpOut;
        }
        return out;
    }
}
