package io.vulpuslabs.patches.dsp.filters.svf;

import io.vulpuslabs.patches.dsp.core.DspMath;
import java.util.function.Consumer;

/**
 * Frozen Chamberlin SVF coefficients: frequency sweep {@code f} and damping
 * {@code qDamp}, correct by construction. The canonical constructor
 * stability-clamps {@code f} for the given {@code qDamp}, so every
 * {@code SvfCoeffs} is a stable (f, d) pair. Mirrors
 * {@code patches_dsp::svf::SvfCoeffs}.
 *
 * <p>Implements {@link Consumer Consumer&lt;float[]&gt;} so it can be handed
 * straight to a coefficient ramp ({@code CoefRamp.setStatic}/{@code beginRamp}),
 * writing {@code f} and {@code qDamp} into the ramp's buffer.
 *
 * @param f     frequency coefficient {@code f = 2 · sin(π · fc / fs)}, clamped
 *              for stability
 * @param qDamp damping coefficient {@code q_damp = 2 · 0.005^q}
 */
public record SvfCoeffs(float f, float qDamp) implements Consumer<float[]> {

    // Coefficient buffer layout, shared with SvfKernel: f at 0, q at 1.
    private static final int F = 0;
    private static final int Q = 1;

    /** Stability-clamp {@code f} for the given damping, so the pair is always stable. */
    public SvfCoeffs {
        f = stabilityClamp(f, qDamp);
    }

    /** Compute coefficients from cutoff (Hz), sample rate (Hz), and normalised Q [0, 1]. */
    public static SvfCoeffs of(float cutoffHz, float sampleRate, float qNorm) {
        return new SvfCoeffs(svfF(cutoffHz, sampleRate), qToDamp(qNorm));
    }

    /** Write {@code f} and {@code qDamp} into a coefficient buffer. */
    @Override
    public void accept(float[] v) {
        v[F] = f;
        v[Q] = qDamp;
    }

    /**
     * Chamberlin frequency coefficient from cutoff Hz:
     * {@code f = 2 · sin(π · fc / fs)}, with {@code fc} clamped to
     * {@code [1, 0.499 · fs]} to stay in the numerically stable region.
     */
    private static float svfF(float cutoffHz, float sampleRate) {
        float fc = DspMath.clamp(cutoffHz, 1.0f, sampleRate * 0.499f);
        return 2.0f * (float) Math.sin(DspMath.FLOAT_PI * fc / sampleRate);
    }

    /**
     * Map normalised Q {@code [0, 1]} to the SVF damping coefficient via an
     * exponential curve: {@code q_damp = 2 · 0.005^q}.
     *
     * <table>
     * <caption>Damping curve</caption>
     * <tr><th>q</th><th>damping</th><th>Q (approx)</th></tr>
     * <tr><td>0.0</td><td>2.0</td><td>0.5 (no peak)</td></tr>
     * <tr><td>0.5</td><td>0.14</td><td>7</td></tr>
     * <tr><td>0.9</td><td>0.014</td><td>70</td></tr>
     * <tr><td>1.0</td><td>0.01</td><td>100 (self-oscillating)</td></tr>
     * </table>
     */
    private static float qToDamp(float q) {
        return 2.0f * (float) Math.pow(0.005f, q);
    }

    /**
     * Clamp {@code f} so the Chamberlin SVF stays stable for damping {@code d}.
     *
     * <p>The characteristic polynomial is
     * {@code λ² − (2−f²−fd)λ + (1−fd)}; the Jury test puts both roots inside the
     * unit circle iff {@code f² + 2fd < 4}. Solving the boundary for {@code f}
     * gives {@code f < −d + √(d² + 4)}; a 0.05 margin keeps the filter off the
     * boundary where transient gains can overflow {@code float}.
     */
    private static float stabilityClamp(float f, float d) {
        float fMax = (-d + (float) Math.sqrt(d * d + 4.0f)) - 0.05f;
        return Math.min(f, fMax);
    }
}
