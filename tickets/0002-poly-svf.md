# Ticket 0002 — Port poly `PolySvfKernel`

- Epic: [0001 — SVF filter port](../epics/0001-svf-filter-port.md)
- Status: To do
- Date: 2026-06-16

## Summary

Port the 16-voice `PolySvfKernel` from `patches_dsp::svf`, reusing the
`dsp-core` ramp primitive and the golden harness scaffolded in ticket 0001.

## Port scope

`dsp-core`:

- `PolyCoefRamp` / `PolyCoefTargets` — the Structure-of-Arrays poly form of the
  ramp primitive (`[[f32; N]; K]` → `float[K][N]`, or flattened `float[K*N]`;
  pick the layout that keeps the inner per-voice loop tight). The Rust inner
  `for i in 0..N` loop is the autovectorisation candidate; keep it branch-free.

`dsp-filters`:

- `PolySvfKernel`: SoA state (`lpState[16]`, `bpState[16]`), `newStatic`,
  `fromCoeffs`, `setStatic`, `beginRampVoice`, `resetState`, and `tickAll`.
- `tickAll(float[] x, boolean ramp)` runs each step of the recurrence as a
  separate loop over the 16 voices, writing `lp`/`hp`/`bp` output arrays.
  Keep the output arrays caller-owned or kernel-owned (no per-call allocation)
  so the audio path stays allocation-free, matching the mono kernel's pattern.

## Layout fidelity

Per ADR 0001, keep the SoA layout close to the Rust struct: hot fields
(`coefs.active`, `coefs.delta`, `lpState`, `bpState`) before the cold `targets`,
so the cache-line behaviour `tickAll` relies on is preserved. Note in code that
this layout is what makes the voice loop the vectorisable axis (never time).

## Correctness tests

Port the poly-specific Rust tests:

- **Poly == mono parity**: all 16 voices driven with identical coeffs/input
  produce output identical to `SvfKernel` (tol 1e-9).
- **Voice independence**: two voices at different cutoffs diverge in state.
- **Poly determinism**: two identical kernels produce bit-identical output.
- **Poly reset**: `resetState` zeroes all 16 voices' integrators without
  touching coefficients.
- **T5 poly stability**: the ADSR FM sweep at high Q across all 16 voices stays
  finite.

### Golden-vector parity

Extend `svf_golden.json` (or add `svf_poly_golden.json`) with at least one
multi-voice scenario dumped from the **real Rust `PolySvfKernel`** — distinct
per-voice cutoffs and a per-voice `beginRampVoice` schedule — and assert the
Java port matches per voice. Regenerate via the same throwaway `cargo` example
approach used in ticket 0001 (add, dump, delete).

## Benchmark

JMH throughput of `tickAll` over a block, across all 16 voices, static and
ramping. Report per-voice cost vs the mono kernel. Do **not** claim SIMD
speedup unless `-XX:+TraceSuperWord` / `PrintAssembly` on a host-matched
OpenJDK 11 confirms the voice loop actually packed (ADR 0001, methodology §5).

## Acceptance criteria

- [ ] `PolySvfKernel` + `PolyCoefRamp` ported; `./gradlew build` green at `--release 17`.
- [ ] Poly==mono parity, voice independence, determinism, reset, and poly T5
      stability tests pass.
- [ ] Golden-vector parity against the real Rust poly kernel passes per voice.
- [ ] JMH throughput reported for `tickAll` (static + ramp), with an honest note
      on whether autovectorisation was verified or merely hoped for.
