// dsp-filters: the non-biquad filter ports. SVF first (ADR 0001), Ladder and
// OTA Ladder to follow.

plugins {
    id("me.champeau.jmh") version "0.7.2"
}

description = "Chamberlin SVF and other non-biquad filter kernels ported from patches-dsp"

dependencies {
    api(project(":dsp-core"))

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(2)
    benchmarkMode.set(listOf("thrpt"))
    // gc profiler reports gc.alloc.rate.norm — bytes allocated per op. ~0 means
    // escape analysis scalar-replaced the per-sample callback closures.
    profilers.add("gc")
}
