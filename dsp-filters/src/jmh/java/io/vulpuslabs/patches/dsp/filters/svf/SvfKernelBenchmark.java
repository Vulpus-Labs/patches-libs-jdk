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
 * Throughput of the mono Chamberlin SVF. Measures samples processed per second
 * over a fixed block, in both the static-coefficient and per-sample-ramp paths.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
public class SvfKernelBenchmark {

    @Param({"512"})
    private int blockSize;

    private float[] input;
    private SvfKernel staticKernel;
    private SvfKernel rampKernel;

    @Setup(Level.Trial)
    public void setup() {
        final float sr = 48_000.0f;
        input = new float[blockSize];
        for (int i = 0; i < blockSize; i++) {
            input[i] = (float) Math.sin(2.0 * Math.PI * 220.0 / sr * i);
        }
        staticKernel = new SvfKernel(SvfCoeffs.of(1_000.0f, sr, 0.5f));
        rampKernel = new SvfKernel(SvfCoeffs.of(1_000.0f, sr, 0.5f), blockSize);
        // Prime a non-zero ramp so the advance step does real work each sample.
        rampKernel.beginRamp(SvfCoeffs.of(4_000.0f, sr, 0.5f));
    }

    /**
     * One block of samples through the static-coefficient path. The returned
     * {@link FilterOutputs} record is decomposed straight into the blackhole, so
     * gc.alloc.rate.norm shows whether escape analysis scalar-replaced it.
     */
    @Benchmark
    public void tickStaticBlock(Blackhole bh) {
        SvfKernel k = staticKernel;
        for (int i = 0; i < input.length; i++) {
            FilterOutputs o = k.tick(input[i]);
            bh.consume(o.lp());
            bh.consume(o.hp());
            bh.consume(o.bp());
        }
    }

    /** One block of samples through the per-sample coefficient-ramp path. */
    @Benchmark
    public void tickRampBlock(Blackhole bh) {
        SvfKernel k = rampKernel;
        for (int i = 0; i < input.length; i++) {
            bh.consume(k.tick(input[i]).lp());
        }
    }
}
