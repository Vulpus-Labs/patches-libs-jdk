# Epic 0001 — SVF filter port

- Status: In progress
- Date: 2026-06-16
- ADR: [0001 — Goals and methodology](../adrs/0001-goals-and-methodology.md)

## Goal

Port the Chamberlin state-variable filter (SVF) from the Rust `patches-dsp`
crate (`src/svf/mod.rs`) to the Java library, in both its single-voice and
16-voice polyphonic forms, with provable numerical fidelity to the Rust
original and a benchmark baseline.

This is the first filter in the broader non-biquad filter port (SVF, Ladder,
OTA Ladder — see ADR 0001). SVF goes first because it is the simplest topology
and exercises every piece of shared machinery the later filters will reuse:
the coefficient-ramp primitive, the stability clamp, NaN/Inf sanitisation, and
the golden-vector parity harness.

## Scope

In:

- `dsp-core`: the `CoefRamp` / `CoefTargets` coefficient-ramp primitive and
  `DspMath` sanitisation helpers, ported from `patches_dsp::coef_ramp`.
- `dsp-filters`: the mono `SvfKernel` and the poly `PolySvfKernel`, plus the
  `Svf` coefficient helpers (`svfF`, `qToDamp`, `stabilityClamp`) and the
  `SvfCoeffs` value type.
- A golden-vector parity harness driven by vectors dumped from the real Rust
  kernel.
- JMH throughput benchmarks for both kernels.

Out:

- Ladder / OTA Ladder filters (separate epic).
- Any Voltage Modular module wrapping these kernels (comes after the port).
- Cross-voice vectorisation work — deferred per ADR 0001 until the host
  polyphony model is known and profiling justifies it.

## Deliverables

A `dsp-filters` jar (Java 11 bytecode) exposing `SvfKernel` and
`PolySvfKernel`, depending on a `dsp-core` jar, each backed by a test suite
that mirrors the Rust kernel's tests and a golden-vector parity check.

## Tickets

| # | Title | Status |
|---|-------|--------|
| [0001](../tickets/0001-mono-svf.md) | Port mono `SvfKernel` + harness scaffolding | Done |
| [0002](../tickets/0002-poly-svf.md) | Port poly `PolySvfKernel` | To do |

## Fidelity bar

Per ADR 0001: each ported kernel reproduces its Rust counterpart's output on
shared golden vectors. The recurrence is identical `f32` arithmetic, so the
only platform-dependent step is the one-off `sin`/`pow` coefficient
evaluation; parity holds well inside 1e-4 absolute on kernel outputs and 1e-6
on the coefficients themselves.
