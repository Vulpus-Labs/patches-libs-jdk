package io.vulpuslabs.patches.dsp.filters.svf;

/**
 * Receiver for the three outputs of one {@link SvfKernel#tick} sample: lowpass,
 * highpass, and bandpass. Passing the outputs into a sink keeps the audio path
 * allocation-free without exposing the kernel's state as fields or getters.
 */
@FunctionalInterface
public interface SvfSink {

    /**
     * @param lp lowpass output (pre-sanitise, matching the Rust kernel's return)
     * @param hp highpass output
     * @param bp bandpass output (pre-sanitise)
     */
    void accept(float lp, float hp, float bp);
}
