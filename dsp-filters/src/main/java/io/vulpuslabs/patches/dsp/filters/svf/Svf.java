package io.vulpuslabs.patches.dsp.filters.svf;

import io.vulpuslabs.patches.dsp.core.DspMath;

/**
 * Coefficient helpers for the Chamberlin state-variable filter.
 *
 * <p>Direct port of the free functions in {@code patches_dsp::svf}. All
 * arithmetic is performed in {@code float} to mirror the Rust {@code f32}
 * kernel; transcendental functions ({@code sin}, {@code pow}, {@code sqrt})
 * are evaluated in {@code double} and narrowed, which agrees with the Rust
 * {@code f32} results to within the project's fidelity tolerance.
 */
public final class Svf {

    /** {@code f32}-precision &pi;, matching Rust's {@code std::f32::consts::PI}. */
    static final float PI = (float) Math.PI;

    private Svf() {
    }

    /**
     * Chamberlin frequency coefficient from cutoff Hz:
     * {@code f = 2 · sin(π · fc / fs)}, with {@code fc} clamped to
     * {@code [1, 0.499 · fs]} to stay in the numerically stable region.
     */
    public static float svfF(float cutoffHz, float sampleRate) {
        float fc = DspMath.clamp(cutoffHz, 1.0f, sampleRate * 0.499f);
        return 2.0f * (float) Math.sin(PI * fc / sampleRate);
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
    public static float qToDamp(float q) {
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
    public static float stabilityClamp(float f, float d) {
        float fMax = (-d + (float) Math.sqrt(d * d + 4.0f)) - 0.05f;
        return Math.min(f, fMax);
    }
}
