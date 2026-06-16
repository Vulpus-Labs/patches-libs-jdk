package io.vulpuslabs.patches.dsp.filters.svf;

/**
 * Frozen Chamberlin SVF coefficients: frequency sweep {@code f} and damping
 * {@code qDamp}. Mirrors {@code patches_dsp::svf::SvfCoeffs}.
 *
 * @param f     frequency coefficient {@code f = 2 · sin(π · fc / fs)}
 * @param qDamp damping coefficient {@code q_damp = 2 · 0.005^q}
 */
public record SvfCoeffs(float f, float qDamp) {

    /** Compute coefficients from cutoff (Hz), sample rate (Hz), and normalised Q [0, 1]. */
    public static SvfCoeffs of(float cutoffHz, float sampleRate, float qNorm) {
        return new SvfCoeffs(Svf.svfF(cutoffHz, sampleRate), Svf.qToDamp(qNorm));
    }
}
