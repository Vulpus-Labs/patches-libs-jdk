package io.vulpuslabs.patches.dsp.core;

import java.util.function.Consumer;
import java.util.Arrays;

/**
 * A coefficient ramp: {@code active} values and per-sample {@code deltas},
 * read/written every sample by a filter's recurrence and advance step, plus a
 * cold {@code targets} store read only at ramp boundaries.
 *
 * <p>Mirrors {@code patches_dsp::coef_ramp::CoefRamp<K>}. The Rust type is
 * generic over a compile-time arity {@code K}; here the arity is the array
 * length fixed at construction. The separate target store is folded in so a
 * single object owns active, deltas, and targets.
 *
 * <h2>No {@code remaining} counter</h2>
 * Drift is handled by snapping {@code active} &larr; the stored target at the
 * <em>start</em> of the next {@link #beginRamp}. There is no per-sample
 * "span finished" check: the ramp keeps advancing until the next update, which
 * re-bases off the stored target (not the drifted active).
 *
 * <p>Values are mutated through {@link Consumer Consumer&lt;float[]&gt;}
 * callbacks rather than passed-in arrays so the caller writes straight into the
 * ramp's own buffers and the audio path stays allocation-free.
 */
public final class CoefRamp {

    private final int size;
    /** {@code 1.0 / update_interval}, precomputed so {@link #beginRamp} divides once at construction. */
    private final float intervalRecip;
    private float[] targets;
    /** Active (interpolating) coefficient values. */
    private float[] active;
    /** Per-sample increment applied by {@link #advance()}. */
    private final float[] deltas;

    /**
     * Create a ramp of {@code size} coefficients, all zero, that interpolates a
     * new target over {@code interval} samples.
     */
    public CoefRamp(int size, int interval) {
        this.size = size;
        this.active = new float[size];
        this.targets = new float[size];
        this.deltas = new float[size];
        this.intervalRecip = 1.0f / interval;
    }

    /**
     * Snap active to the values written by {@code valueSetter}, mirror them into
     * the target store (so the next {@link #beginRamp} snap stays coherent), and
     * zero the deltas.
     */
    public void setStatic(Consumer<float[]> valueSetter) {
        valueSetter.accept(active);
        System.arraycopy(active, 0, targets, 0, size);
        Arrays.fill(deltas, 0.0f);
    }

    /**
     * Snap active &larr; the stored targets (eliminating drift), let
     * {@code targetSetter} write the new targets, and compute per-sample deltas
     * over the construction-time interval.
     *
     * <p>The snap reuses the old active buffer as the new target buffer via a
     * swap, so this allocates nothing.
     *
     * @param targetSetter writes the new target values into the supplied buffer
     */
    public void beginRamp(Consumer<float[]> targetSetter) {
        float[] swap = active;
        active = targets;
        targets = swap;
        targetSetter.accept(targets);
        for (int k = 0; k < size; k++) {
            deltas[k] = (targets[k] - active[k]) * intervalRecip;
        }
    }

    /**
     * {@code active[k] += delta[k]} for all k. Call once per sample after the
     * kernel's recurrence step. Returns the (now-advanced) active buffer.
     */
    public float[] advance() {
        for (int k = 0; k < size; k++) {
            active[k] += deltas[k];
        }
        return active;
    }

    /** Live active-value buffer; read the current coefficients before {@link #advance()}. */
    public float[] active() {
        return active;
    }

    /** Live per-sample delta buffer. */
    public float[] deltas() {
        return deltas;
    }

    /** Live target buffer, last committed at a {@link #beginRamp}/{@link #setStatic} boundary. */
    public float[] targets() {
        return targets;
    }
}
