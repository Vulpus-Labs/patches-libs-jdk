package io.vulpuslabs.patches.dsp.filters.svf;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Throughput of the 16-voice {@link PolySvfKernel} over a block, static and
 * ramping. Compare per-voice cost against {@link SvfKernelBenchmark} to see what
 * (if anything) the voice-loop autovectorisation buys. Do not claim a SIMD
 * speedup without confirming SuperWord packed the loop (TraceSuperWord / asm).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
public class PolySvfBenchmark {

    @Param({"512"})
    private int blockSize;

    @Param({"16"})
    private int voices;

    private float[][] input;
    private float[] lp;
    private float[] hp;
    private float[] bp;
    private PolySvfKernel staticKernel;
    private PolySvfKernel rampKernel;

    @Setup(Level.Trial)
    public void setup() {
        final float sr = 48_000.0f;
        input = new float[blockSize][voices];
        for (int i = 0; i < blockSize; i++) {
            for (int v = 0; v < voices; v++) {
                input[i][v] = (float) Math.sin(2.0 * Math.PI * (110.0 + 20.0 * v) / sr * i);
            }
        }
        lp = new float[voices];
        hp = new float[voices];
        bp = new float[voices];

        SvfCoeffs[] base = new SvfCoeffs[voices];
        SvfCoeffs[] target = new SvfCoeffs[voices];
        for (int v = 0; v < voices; v++) {
            base[v] = SvfCoeffs.of(1_000.0f + 50.0f * v, sr, 0.5f);
            target[v] = SvfCoeffs.of(4_000.0f + 50.0f * v, sr, 0.5f);
        }
        staticKernel = new PolySvfKernel(base);
        rampKernel = new PolySvfKernel(base, blockSize);
        rampKernel.beginRamp(target);
    }

    /** One block, all voices, static coefficients. */
    @Benchmark
    public void tickAllStatic(Blackhole bh) {
        PolySvfKernel k = staticKernel;
        for (int i = 0; i < blockSize; i++) {
            k.tickAll(input[i], lp, hp, bp);
            bh.consume(lp);
            bh.consume(hp);
            bh.consume(bp);
        }
    }

    /** One block, all voices, per-sample coefficient ramp. */
    @Benchmark
    public void tickAllRamp(Blackhole bh) {
        PolySvfKernel k = rampKernel;
        for (int i = 0; i < blockSize; i++) {
            k.tickAll(input[i], lp, hp, bp);
            bh.consume(lp);
        }
    }
}
