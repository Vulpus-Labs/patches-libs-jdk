package io.vulpuslabs.patches.dsp.filters.svf;

import io.vulpuslabs.patches.dsp.core.CoefRamp;
import java.util.Arrays;

/**
 * 16-voice (or N-voice) Chamberlin SVF, structured so the per-voice recurrence
 * is a flat array loop the C2 autovectorizer (SuperWord/SLP) can pack. Poly form
 * of {@link SvfKernel}.
 *
 * <p>The vectorisable axis is <em>voices, never time</em>: the filter is IIR,
 * serial along the time axis, but every voice's recurrence step is independent,
 * so {@link #tickAll} runs each step as one loop over the voices.
 *
 * <h2>Layout</h2>
 * Structure-of-Arrays, unit-stride per quantity so SuperWord sees contiguous
 * loads. Coefficients live in a single {@link CoefRamp} buffer of {@code 2·N}
 * floats, <em>blocked</em> (not interleaved): {@code f} in {@code [0, N)} and
 * {@code qDamp} in {@code [N, 2N)}, so {@code f[v] = active[v]} and
 * {@code q[v] = active[N + v]} are each contiguous across voices. State and
 * outputs are plain {@code float[N]}.
 *
 * <p>All voices ramp together over the construction-time interval (matching the
 * host's per-block parameter updates); {@link #setStatic}/{@link #beginRamp}
 * take one {@link SvfCoeffs} per voice.
 */
public final class PolySvfKernel {

    private final int voices;
    /** Coefficients, blocked: f in [0, voices), qDamp in [voices, 2·voices). */
    private final CoefRamp coefs;
    private final float[] lpState;
    private final float[] bpState;

    /** Create a static (non-ramping) poly kernel, one {@link SvfCoeffs} per voice. */
    public PolySvfKernel(SvfCoeffs[] perVoice) {
        this(perVoice, 1);
    }

    /**
     * Create a poly kernel whose coefficients ramp toward new per-voice targets
     * over {@code interval} samples.
     */
    public PolySvfKernel(SvfCoeffs[] perVoice, int interval) {
        this.voices = perVoice.length;
        this.coefs = new CoefRamp(2 * voices, interval);
        this.lpState = new float[voices];
        this.bpState = new float[voices];
        coefs.setStatic(buf -> writeBlocked(buf, perVoice));
    }

    /** Number of voices. */
    public int voices() {
        return voices;
    }

    /** Immediately snap every voice's coefficients to {@code perVoice} with no ramp. */
    public void setStatic(SvfCoeffs[] perVoice) {
        coefs.setStatic(buf -> writeBlocked(buf, perVoice));
    }

    /** Begin a ramp toward new per-voice targets {@code perVoice}. */
    public void beginRamp(SvfCoeffs[] perVoice) {
        coefs.beginRamp(buf -> writeBlocked(buf, perVoice));
    }

    private void writeBlocked(float[] buf, SvfCoeffs[] perVoice) {
        int n = voices;
        for (int v = 0; v < n; v++) {
            buf[v] = perVoice[v].f();
            buf[n + v] = perVoice[v].qDamp();
        }
    }

    /** Reset all voices' integrator state to zero without touching coefficients. */
    public void resetState() {
        Arrays.fill(lpState, 0.0f);
        Arrays.fill(bpState, 0.0f);
    }

    /**
     * Run one sample for every voice and advance the coefficient ramp. Inputs and
     * the three output buffers are caller-owned and length {@code voices()}, so
     * the audio path allocates nothing. The recurrence is one flat loop over the
     * voices — the autovectorisation candidate.
     */
    public void tickAll(float[] x, float[] lpOut, float[] hpOut, float[] bpOut) {
        coefs.advance(active -> {
            int n = voices;
            for (int v = 0; v < n; v++) {
                float f = active[v];
                float q = active[n + v];
                float lp = lpState[v] + f * bpState[v];
                float hp = x[v] - lp - q * bpState[v];
                float bp = bpState[v] + f * hp;
                lpState[v] = lp;
                bpState[v] = bp;
                lpOut[v] = lp;
                hpOut[v] = hp;
                bpOut[v] = bp;
            }
            return null;
        });
    }
}
