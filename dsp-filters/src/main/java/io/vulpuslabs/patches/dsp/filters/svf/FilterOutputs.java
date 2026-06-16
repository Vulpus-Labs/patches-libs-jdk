package io.vulpuslabs.patches.dsp.filters.svf;

/**
 * The outputs of one SVF sample: lowpass, highpass, and bandpass, with the
 * notch response derived on demand.
 *
 * <p>Returned by {@link SvfKernel#tick(float)}. On the audio hot path this is
 * a transient value the JIT is expected to scalar-replace (escape analysis),
 * so no object actually reaches the heap when the caller decomposes it
 * immediately.
 */
public record FilterOutputs(float lp, float hp, float bp) {

    /** Notch (band-reject) output: {@code lowpass + highpass}. */
    public float notch() {
        return lp + hp;
    }
}
