package io.vulpuslabs.patches.dsp.filters.svf;

/**
 * Caller-owned output buffers for {@link PolySvfKernel#tickAll}: one
 * {@code float[voices]} each for the lowpass, highpass, and bandpass responses.
 * {@code update} is called per voice inside the tick; {@code get} reads one
 * voice back as a {@link FilterOutputs}.
 */
public record PolyFilterOutputs(float[] lp, float[] hp, float[] bp) {

    /** Default voice count when none is given. */
    public static final int DEFAULT_VOICES = 16;

    /** Allocate output buffers for {@code voices} voices. */
    public static PolyFilterOutputs create(int voices) {
        return new PolyFilterOutputs(new float[voices], new float[voices], new float[voices]);
    }

    /** Allocate output buffers for {@value #DEFAULT_VOICES} voices. */
    public static PolyFilterOutputs create() {
        return create(DEFAULT_VOICES);
    }

    public void update(int index, float lp, float hp, float bp) {
        this.lp[index] = lp;
        this.hp[index] = hp;
        this.bp[index] = bp;
    }

    public FilterOutputs get(int index) {
        return new FilterOutputs(lp[index], hp[index], bp[index]);
    }
}
