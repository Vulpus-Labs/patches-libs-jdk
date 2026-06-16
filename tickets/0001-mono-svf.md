# Ticket 0001 — Port mono `SvfKernel` + harness scaffolding

- Epic: [0001 — SVF filter port](../epics/0001-svf-filter-port.md)
- Status: Done
- Date: 2026-06-16

## Summary

Stand up the multi-module Gradle project and port the single-voice Chamberlin
`SvfKernel` from `patches_dsp::svf`, with a correctness test suite and a JMH
benchmark. This ticket also lays the shared scaffolding (`dsp-core`, the
golden-vector harness, the build conventions) that the poly port and later
filters build on.

## Build / project setup

- Multi-module Gradle build, Kotlin DSL: root + `dsp-core` + `dsp-filters`.
- Java toolchain 21 (Temurin/Adoptium), compiled `--release 11` so the
  deliverable is Java 11 bytecode (verified: class major version 55), per
  ADR 0001. **No records / Java 12+ APIs** in `main` — `--release 11` rejects
  them at compile time, which is the intended guard.
- The Gradle daemon is pinned to Temurin 21 in `gradle.properties` because the
  machine default JDK (GraalVM 26) cannot be parsed by Gradle 8.14's bundled
  Kotlin compiler, and HotSpot is the runtime we want benchmarks to reflect.
- Each module emits a library jar + sources jar.

## Port scope

`dsp-core`:

- `CoefRamp` / `CoefTargets` — the snap-on-begin / store-target / compute-delta
  / per-sample-advance ramp primitive (`patches_dsp::coef_ramp`). Rust const
  generics over arity `K` become array-length-at-construction.
- `DspMath.sanitize` (NaN/Inf → 0) and `DspMath.clamp`.

`dsp-filters`:

- `Svf` coefficient helpers: `svfF`, `qToDamp`, `stabilityClamp`. Arithmetic in
  `float` to mirror Rust `f32`; transcendentals evaluated in `double` and
  narrowed.
- `SvfCoeffs` immutable value type.
- `SvfKernel`: `new SvfKernel(f, d)`, `fromCoeffs`, `setStatic`, `beginRamp`,
  `resetState`, `tick`. `tick` is allocation-free — it writes `lpOut`/`hpOut`/
  `bpOut` fields rather than returning a tuple, so the audio path allocates
  nothing.

## Correctness tests

Mirror the Rust SVF test families (T1–T7) so the two suites can be cross-checked:

- **T1 impulse** vs a closed-form `f32` recurrence (tol 1e-9).
- **T2 frequency response**: steady-state LP/HP passband and BP peak, plus
  FFT-based full LP/HP/BP magnitude response.
- **T3 DC / Nyquist**: LP passes DC, HP rejects DC, HP passes Nyquist.
- **T4 stability** at high resonance (bounded output over 10k samples).
- **T5 stability** under an ADSR-driven FM sweep at high Q (no NaN/Inf).
- **T6 SNR** of the `f32` LP output vs an inline `f64` reference (≥ 120 dB).
- **T7 determinism**: bit-identical output across runs.
- `CoefRamp` unit tests ported from the Rust `coef_ramp` test module.

### Golden-vector parity (ADR backbone)

`golden/svf_golden.json` was dumped from the **real Rust `SvfKernel`** (a
throwaway `cargo` example against the actual crate, since removed). Three
scenarios — impulse, static sine, and a ramped cutoff sweep exercising
`beginRamp` + `stabilityClamp` — are replayed through the Java port and asserted
to match `lp`/`hp`/`bp` within 1e-4, with coefficients matching within 1e-6.

## Benchmark

JMH (`me.champeau.jmh`) throughput of `tick` over a 512-sample block, on both
the static-coefficient and per-sample-ramp paths.

Baseline (Temurin 21, Apple Silicon, indicative — not a controlled measurement):

| Path | Blocks/s | ≈ Samples/s |
|------|----------|-------------|
| static coeffs | ~368k | ~188 M |
| per-sample ramp | ~267k | ~137 M |

## Acceptance criteria

- [x] `./gradlew build` green: both modules compile `--release 11` and all tests pass.
- [x] Delivered jars are Java 11 bytecode (class major version 55).
- [x] Golden-vector parity against the Rust kernel passes.
- [x] `./gradlew :dsp-filters:jmh` produces throughput numbers for both paths.

## Notes for the poly ticket

The harness, `dsp-core` primitives, golden-dump approach, and build conventions
are now in place; ticket 0002 only needs the `PolySvfKernel` itself, its
per-voice golden vectors, and a voices-wide benchmark.
