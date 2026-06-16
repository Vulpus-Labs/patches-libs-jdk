package io.vulpuslabs.patches.dsp.filters.svf;

import io.vulpuslabs.patches.dsp.core.CoefRamp;

/**
 * Single-voice Chamberlin SVF kernel with per-sample coefficient interpolation.
 *
 * <p>Direct port of {@code patches_dsp::svf::SvfKernel}. The Chamberlin
 * topology, evaluated once per sample:
 *
 * <pre>
 * lp = lp_state + f · bp_state
 * hp = x - lp - q_damp · bp_state
 * bp = bp_state + f · hp
 * </pre>
 *
 * <p>Coefficients are supplied as {@link SvfCoeffs} values, which are already
 * stability-clamped by construction and write themselves into the ramp buffer.
 *
 * <p>{@link #tick(float)} returns the sample's outputs as a {@link FilterOutputs}
 * record. On the audio hot path that record is a transient the JIT scalar-replaces
 * (escape analysis), so the kernel keeps its state private without a per-sample
 * heap allocation.
 *
 * <p>No NaN/Inf sanitisation: Voltage Modular runs with denormals flushed to zero
 * at the CPU level, and {@link SvfCoeffs}'s stability clamp keeps the recurrence
 * bounded, so the inner loop is a branch-free FMA chain.
 */
public final class SvfKernel {

    // Coefficient index order: f = 0, q = 1.
    private static final int F = 0;
    private static final int Q = 1;

    /** Coefficients: active + per-sample deltas + ramp-boundary targets. */
    private final CoefRamp coefs;

    /** Lowpass integrator state. */
    private float lpState;
    /** Bandpass integrator state. */
    private float bpState;

    /**
     * Create a kernel with static (non-ramping) coefficients {@code c}. Ramps
     * started on this kernel snap in a single sample; use
     * {@link #SvfKernel(SvfCoeffs, int)} to ramp over an interval.
     */
    public SvfKernel(SvfCoeffs c) {
        this(c, 1);
    }

    /**
     * Create a kernel with coefficients {@code c} that ramp toward new targets
     * over {@code interval} samples (the per-sample delta is
     * {@code (target - active) / interval}).
     */
    public SvfKernel(SvfCoeffs c, int interval) {
        this.coefs = new CoefRamp(2, interval);
        coefs.setStatic(c);
    }

    /** Immediately snap all coefficients to {@code c} with no ramp. */
    public void setStatic(SvfCoeffs c) {
        coefs.setStatic(c);
    }

    /**
     * Snap active coefficients to the previous targets, store the new targets
     * {@code c}, and compute per-sample deltas over the construction-time
     * interval. The snapped active values come from the previous (already
     * stability-clamped) {@link SvfCoeffs}, so no re-clamp is needed.
     */
    public void beginRamp(SvfCoeffs c) {
        coefs.beginRamp(c);
    }

    /** Reset integrator state to zero without touching coefficients. */
    public void resetState() {
        lpState = 0.0f;
        bpState = 0.0f;
    }

    /**
     * Run one Chamberlin SVF sample, advance the interpolating coefficients, and
     * return the lowpass/highpass/bandpass outputs.
     */
    public FilterOutputs tick(float x) {
        return coefs.advance(active -> {
            float f = active[F];
            float q = active[Q];

            float lp = lpState + f * bpState;
            float hp = x - lp - q * bpState;
            float bp = bpState + f * hp;
            lpState = lp;
            bpState = bp;

            return new FilterOutputs(lp, hp, bp);
        });
    }
}
