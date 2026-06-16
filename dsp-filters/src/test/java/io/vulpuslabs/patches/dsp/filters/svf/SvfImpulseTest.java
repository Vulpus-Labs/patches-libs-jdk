package io.vulpuslabs.patches.dsp.filters.svf;

import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.SAMPLE_RATE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T1 — impulse response against a closed-form recurrence. */
class SvfImpulseTest {

    @Test
    void t1ImpulseResponseLowpass() {
        float fc = 1_000.0f;
        float qNorm = 0.5f;
        float f = Svf.svfF(fc, SAMPLE_RATE);
        float d = Svf.qToDamp(qNorm);
        SvfKernel kernel = new SvfKernel(f, d);

        float refLp = 0.0f;
        float refBp = 0.0f;
        for (int i = 0; i < 64; i++) {
            float x = (i == 0) ? 1.0f : 0.0f;
            float refLpNew = refLp + f * refBp;
            float refHp = x - refLpNew - d * refBp;
            float refBpNew = refBp + f * refHp;
            refLp = refLpNew;
            refBp = refBpNew;

            kernel.tick(x);
            assertTrue(Math.abs(kernel.lpOut - refLp) < 1e-9f,
                    "sample " + i + ": lp=" + kernel.lpOut + " ref=" + refLp);
        }
    }
}
