package io.vulpuslabs.patches.dsp.filters.svf;

import io.vulpuslabs.patches.dsp.core.CoefRamp;
import io.vulpuslabs.patches.dsp.core.DspMath;

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
 * <p>{@link #tick(float, SvfSink)} passes its three outputs to a {@link SvfSink}
 * rather than returning or publishing them, so the audio path is allocation-free
 * and the kernel keeps its state private. The {@code lp}/{@code bp} handed to the
 * sink are the pre-sanitise values (matching the Rust kernel's return), while the
 * stored integrator state is the sanitised value.
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
     * hand the lowpass/highpass/bandpass outputs to {@code sink}.
     */
    public void tick(float x, SvfSink sink) {
        coefs.advance(active -> {
            float f = active[F];
            float q = active[Q];

            lpState = DspMath.sanitize(lpState + f * bpState);
            float hp = x - lpState - q * bpState;
            bpState = DspMath.sanitize(bpState + f * hp);

            sink.accept(lpState, hp, bpState);
        });
    }
}
