package io.vulpuslabs.patches.dsp.filters.svf;

import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.BP;
import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.HP;
import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.LP;
import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.SAMPLE_RATE;
import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.db;
import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.magnitudeResponseDb;
import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.makeKernel;
import static io.vulpuslabs.patches.dsp.filters.svf.SvfTestSupport.measureSteadyStateAmplitude;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T2 — steady-state and FFT-based frequency response. */
class SvfFrequencyResponseTest {

    @Test
    void t2LowpassPassband() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.0f);
        float amp = measureSteadyStateAmplitude(kernel, 100.0f, LP);
        double dbErr = Math.abs(db(amp));
        assertTrue(dbErr < 1.0, "LP passband: amp=" + amp + " dB_from_unity=" + dbErr);
    }

    @Test
    void t2HighpassPassband() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.0f);
        float amp = measureSteadyStateAmplitude(kernel, 10_000.0f, HP);
        assertTrue(amp > 0.7f && amp < 1.5f, "HP passband: amp=" + amp);
    }

    @Test
    void t2BandpassPeak() {
        float fc = 1_000.0f;
        SvfCoeffs c = SvfCoeffs.of(fc, SAMPLE_RATE, 0.5f);
        float d = c.qDamp();
        SvfKernel kernel = new SvfKernel(c);
        float amp = measureSteadyStateAmplitude(kernel, fc, BP);
        double theoretical = 1.0 / d;
        double dbErr = Math.abs(db(amp / theoretical));
        assertTrue(dbErr < 1.0, "BP peak: amp=" + amp + " theoretical=" + theoretical + " dB_err=" + dbErr);
    }

    @Test
    void lowpassFrequencyResponseFull() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.0f);
        int fftSize = 1024;
        float[] ir = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            final int bin = i;
            kernel.tick(i == 0 ? 1.0f : 0.0f, (lp, hp, bp) -> ir[bin] = lp);
        }
        double[] resp = magnitudeResponseDb(ir, fftSize);

        int passbandEnd = (int) Math.floor(500.0 * fftSize / SAMPLE_RATE);   // 10
        int stopbandStart = (int) Math.ceil(4_000.0 * fftSize / SAMPLE_RATE); // 86
        int nyquist = fftSize / 2;

        assertPassbandFlat(resp, 1, passbandEnd, 2.0);
        for (int bin = stopbandStart; bin <= nyquist; bin++) {
            assertTrue(resp[bin] < -12.0, "stopband bin " + bin + " = " + resp[bin] + " dB, expected < -12");
        }
    }

    @Test
    void highpassFrequencyResponseFull() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.0f);
        int fftSize = 1024;
        float[] ir = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            final int bin = i;
            kernel.tick(i == 0 ? 1.0f : 0.0f, (lp, hp, bp) -> ir[bin] = hp);
        }
        double[] resp = magnitudeResponseDb(ir, fftSize);

        int stopbandEnd = (int) Math.floor(200.0 * fftSize / SAMPLE_RATE);    // 4
        int passbandStart = (int) Math.ceil(4_000.0 * fftSize / SAMPLE_RATE);  // 86
        int passbandEnd = (int) Math.floor(20_000.0 * fftSize / SAMPLE_RATE);  // 426

        for (int bin = 1; bin <= stopbandEnd; bin++) {
            assertTrue(resp[bin] < -12.0, "stopband bin " + bin + " = " + resp[bin] + " dB, expected < -12");
        }
        assertPassbandFlat(resp, passbandStart, passbandEnd, 3.0);
    }

    @Test
    void bandpassFrequencyResponseFull() {
        SvfKernel kernel = makeKernel(1_000.0f, 0.5f);
        int fftSize = 1024;
        float[] ir = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            final int bin = i;
            kernel.tick(i == 0 ? 1.0f : 0.0f, (lp, hp, bp) -> ir[bin] = bp);
        }
        double[] resp = magnitudeResponseDb(ir, fftSize);

        int expectedBin = Math.round(1_000.0f * fftSize / SAMPLE_RATE); // 21
        // Peak within ±2 bins of expected.
        int peakBin = 0;
        double peakDb = Double.NEGATIVE_INFINITY;
        for (int bin = 1; bin <= fftSize / 2; bin++) {
            if (resp[bin] > peakDb) {
                peakDb = resp[bin];
                peakBin = bin;
            }
        }
        assertTrue(Math.abs(peakBin - expectedBin) <= 2,
                "BP peak at bin " + peakBin + ", expected ≈ " + expectedBin);

        int lowEnd = (int) Math.floor(100.0 * fftSize / SAMPLE_RATE); // 2
        for (int bin = 1; bin <= lowEnd; bin++) {
            assertTrue(resp[bin] <= peakDb - 12.0,
                    "bin " + bin + " = " + resp[bin] + " dB should be ≥12 dB below peak " + peakDb);
        }
        int highStart = (int) Math.ceil(10_000.0 * fftSize / SAMPLE_RATE); // 214
        for (int bin = highStart; bin <= fftSize / 2; bin++) {
            assertTrue(resp[bin] <= peakDb - 12.0,
                    "bin " + bin + " = " + resp[bin] + " dB should be ≥12 dB below peak " + peakDb);
        }
    }

    private static void assertPassbandFlat(double[] resp, int lo, int hi, double tolDb) {
        for (int bin = lo; bin <= hi; bin++) {
            assertTrue(Math.abs(resp[bin]) <= tolDb,
                    "passband bin " + bin + " = " + resp[bin] + " dB, expected |dB| ≤ " + tolDb);
        }
    }
}
