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
 * <p>{@link #tick(float)} returns nothing and writes its three outputs to the
 * public {@link #lpOut}/{@link #hpOut}/{@link #bpOut} fields, so the audio path
 * is allocation-free. The returned {@code lp}/{@code bp} are the pre-sanitise
 * values (matching the Rust kernel's return), while the stored integrator state
 * is the sanitised value.
 */
public final class SvfKernel {

    // Coefficient index order: f = 0, q = 1.
    private static final int F = 0;
    private static final int Q = 1;

    /** Coefficients: active + per-sample deltas + ramp-boundary targets. */
    public final CoefRamp coefs;

    /** Lowpass integrator state. */
    public float lpState;
    /** Bandpass integrator state. */
    public float bpState;

    /** Last lowpass output from {@link #tick(float)}. */
    public float lpOut;
    /** Last highpass output from {@link #tick(float)}. */
    public float hpOut;
    /** Last bandpass output from {@link #tick(float)}. */
    public float bpOut;

    /**
     * Create a kernel with static (non-ramping) coefficients {@code f},
     * {@code d}. Ramps started on this kernel snap in a single sample; use
     * {@link #SvfKernel(float, float, int)} to ramp over an interval.
     */
    public SvfKernel(float f, float d) {
        this(f, d, 1);
    }

    /**
     * Create a kernel with coefficients {@code f}, {@code d} that ramp toward
     * new targets over {@code interval} samples (the per-sample delta is
     * {@code (target - active) / interval}).
     */
    public SvfKernel(float f, float d, int interval) {
        float clamped = Svf.stabilityClamp(f, d);
        this.coefs = new CoefRamp(2, interval);
        coefs.setStatic(v -> {
            v[F] = clamped;
            v[Q] = d;
        });
    }

    /** Create from an {@link SvfCoeffs} value. */
    public static SvfKernel fromCoeffs(SvfCoeffs c) {
        return new SvfKernel(c.f(), c.qDamp());
    }

    /** Immediately snap all coefficients to {@code f} / {@code d} with no ramp. */
    public void setStatic(float f, float d) {
        float clamped = Svf.stabilityClamp(f, d);
        coefs.setStatic(v -> {
            v[F] = clamped;
            v[Q] = d;
        });
    }

    /**
     * Snap active coefficients to the previous targets, store new targets, and
     * compute per-sample deltas over the construction-time interval.
     *
     * <p>Applies {@link Svf#stabilityClamp} to the new frequency target. The
     * snapped active {@code f} needs no re-clamp: the stored target is always a
     * value previously passed through {@code stabilityClamp} with its own
     * damping, and {@code stabilityClamp} (a {@code min}) is idempotent.
     *
     * @param ft new frequency-coefficient target
     * @param dt new damping target
     */
    public void beginRamp(float ft, float dt) {
        float clamped = Svf.stabilityClamp(ft, dt);
        coefs.beginRamp(t -> {
            t[F] = clamped;
            t[Q] = dt;
        });
    }

    /** Reset integrator state to zero without touching coefficients. */
    public void resetState() {
        lpState = 0.0f;
        bpState = 0.0f;
    }

    /**
     * Run one Chamberlin SVF sample and advance the interpolating coefficients.
     * Writes {@link #lpOut}, {@link #hpOut}, {@link #bpOut}.
     */
    public void tick(float x) {
        float[] active = coefs.active();
        float f = active[F];
        float q = active[Q];
        float lp = lpState + f * bpState;
        float hp = x - lp - q * bpState;
        float bp = bpState + f * hp;
        lpState = DspMath.sanitize(lp);
        bpState = DspMath.sanitize(bp);
        coefs.advance();
        lpOut = lp;
        hpOut = hp;
        bpOut = bp;
    }
}
