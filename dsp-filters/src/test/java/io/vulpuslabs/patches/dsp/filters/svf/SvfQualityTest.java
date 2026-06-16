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

        SvfKernel kernel = new SvfKernel(SvfCoeffs.of(fc, sr, qNorm));

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

            float[] lp = {0.0f};
            kernel.tick(x32, (l, h, b) -> lp[0] = l);
            sumSqSignal += refLp * refLp;
            double err = lp[0] - refLp;
            sumSqError += err * err;
        }

        double rmsSignal = Math.sqrt(sumSqSignal / n);
        double rmsError = Math.sqrt(sumSqError / n);
        double snrDb = 20.0 * Math.log10(rmsSignal / rmsError);
        assertTrue(snrDb >= 120.0, "SNR too low: " + snrDb + " dB (expected ≥ 120)");
    }

    @Test
    void t7Determinism() {
        SvfCoeffs c = SvfCoeffs.of(800.0f, SAMPLE_RATE, 0.4f);
        float[] input = new float[256];
        for (int i = 0; i < input.length; i++) {
            input[i] = (float) Math.sin(2.0 * Math.PI * 440.0 / SAMPLE_RATE * i);
        }

        float[] a = run(new SvfKernel(c), input);
        float[] b = run(new SvfKernel(c), input);
        for (int i = 0; i < input.length; i++) {
            assertEquals(Float.floatToIntBits(a[i]), Float.floatToIntBits(b[i]),
                    "non-deterministic at sample " + i);
        }
    }

    @Test
    void coeffsRoundTrip() {
        SvfCoeffs c = SvfCoeffs.of(440.0f, SAMPLE_RATE, 0.5f);
        SvfKernel k = new SvfKernel(c);
        // Drive an impulse and compare against the closed-form recurrence built
        // straight from c.f()/c.qDamp(); matching outputs prove both coefficients
        // were wired into the kernel (qDamp shows up once bp is non-zero).
        float f = c.f();
        float d = c.qDamp();
        float refLp = 0.0f;
        float refBp = 0.0f;
        for (int i = 0; i < 16; i++) {
            float x = (i == 0) ? 1.0f : 0.0f;
            float refLpNew = refLp + f * refBp;
            float refHp = x - refLpNew - d * refBp;
            float refBpNew = refBp + f * refHp;
            refLp = refLpNew;
            refBp = refBpNew;

            final float eLp = refLp;
            final float eHp = refHp;
            final float eBp = refBp;
            final int idx = i;
            k.tick(x, (lp, hp, bp) -> {
                assertEquals(eLp, lp, 1e-6f, "lp[" + idx + "]");
                assertEquals(eHp, hp, 1e-6f, "hp[" + idx + "]");
                assertEquals(eBp, bp, 1e-6f, "bp[" + idx + "]");
            });
        }
    }

    private static float[] run(SvfKernel k, float[] input) {
        float[] out = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            final int idx = i;
            k.tick(input[i], (lp, hp, bp) -> out[idx] = lp);
        }
        return out;
    }
}
