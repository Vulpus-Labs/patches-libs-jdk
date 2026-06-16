package io.vulpuslabs.patches.dsp.core;

/** Small numeric helpers shared across DSP kernels. */
public final class DspMath {

    private DspMath() {
    }

    /** {@code f32}-precision &pi;, matching Rust's {@code std::f32::consts::PI}. */
    public static final float FLOAT_PI = (float) Math.PI;


    /**
     * Replace NaN / &plusmn;Inf with {@code 0.0f} so a single bad sample cannot
     * permanently corrupt recursive integrator state.
     *
     * <p>Mirrors {@code patches_dsp::svf::sanitize}.
     */
    public static float sanitize(float v) {
        return Float.isFinite(v) ? v : 0.0f;
    }

    /** Clamp {@code v} into {@code [lo, hi]} (mirrors Rust {@code f32::clamp}). */
    public static float clamp(float v, float lo, float hi) {
        if (v < lo) {
            return lo;
        }
        return Math.min(v, hi);
    }
}
