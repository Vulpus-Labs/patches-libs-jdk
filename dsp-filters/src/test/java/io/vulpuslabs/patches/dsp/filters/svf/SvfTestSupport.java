package io.vulpuslabs.patches.dsp.filters.svf;

import java.util.function.ToDoubleFunction;

/** Shared fixtures and helpers for the SVF tests. Mirrors the Rust test module's helpers. */
final class SvfTestSupport {

    static final float SAMPLE_RATE = 48_000.0f;

    private SvfTestSupport() {
    }

    static SvfKernel makeKernel(float cutoffHz, float qNorm) {
        return new SvfKernel(Svf.svfF(cutoffHz, SAMPLE_RATE), Svf.qToDamp(qNorm));
    }

    static double db(double ratio) {
        return 20.0 * Math.log10(ratio);
    }

    /** Output selector over the three SVF modes of a kernel after a {@code tick}. */
    interface Mode extends ToDoubleFunction<SvfKernel> {
    }

    static final Mode LP = k -> k.lpOut;
    static final Mode HP = k -> k.hpOut;
    static final Mode BP = k -> k.bpOut;

    /**
     * Drive a sinusoid through {@code kernel} and return the steady-state peak
     * amplitude of the selected mode. Mirrors the Rust
     * {@code measure_steady_state_amplitude}.
     */
    static float measureSteadyStateAmplitude(SvfKernel kernel, float freqHz, Mode mode) {
        double omega = 2.0 * Math.PI * freqHz / SAMPLE_RATE;
        for (int i = 0; i < 4096; i++) {
            kernel.tick((float) Math.sin(omega * i));
            mode.applyAsDouble(kernel);
        }
        float peak = 0.0f;
        for (int i = 4096; i < 5120; i++) {
            kernel.tick((float) Math.sin(omega * i));
            float y = (float) mode.applyAsDouble(kernel);
            if (Math.abs(y) > peak) {
                peak = Math.abs(y);
            }
        }
        return peak;
    }

    /**
     * Magnitude response in dB of an impulse response via a direct DFT
     * (O(n²), fine for the n=1024 test signals). Returns bins {@code 0..n/2}.
     */
    static double[] magnitudeResponseDb(float[] ir, int fftSize) {
        int half = fftSize / 2;
        double[] out = new double[half + 1];
        for (int k = 0; k <= half; k++) {
            double re = 0.0;
            double im = 0.0;
            for (int n = 0; n < fftSize; n++) {
                double angle = -2.0 * Math.PI * k * n / fftSize;
                re += ir[n] * Math.cos(angle);
                im += ir[n] * Math.sin(angle);
            }
            double mag = Math.sqrt(re * re + im * im);
            out[k] = 20.0 * Math.log10(mag + 1e-20);
        }
        return out;
    }
}
