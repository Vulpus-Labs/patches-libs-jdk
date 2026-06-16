# ADR 0001 — Goals and methodology for patches-libs-jdk

- Status: Accepted
- Date: 2026-06-15

> **Update 2026-06-16 — Java 17 target.** Voltage Modular's embedded runtime
> now supports the Java 17 instruction set. The deliverable is therefore Java
> 17 bytecode (`--release 17`), not Java 11. Everywhere this document says
> "Java 11 bytecode" / "`--release 11`" / "Java 11 instruction set", read
> "Java 17". This unblocks Java 12–17 language features (records, switch
> expressions, `var`, text blocks). The vectorisation reasoning is unchanged:
> the Vector API is still an incubator module (`jdk.incubator.vector`) absent
> from the default module graph in Java 17, so C2 autovectorization remains the
> only lever; the C2 SuperWord caveats now apply to the host's Java 17 C2.

## Context

The `patches` workspace contains a body of audio DSP — written in Rust, in
`patches-dsp` and in `patches-bundles/patches-vintage` — that is more capable
than the stock building blocks shipped with Cherry Audio's Voltage Modular SDK.
We want to make selected pieces of that DSP available to Voltage Modular module
developers (initially ourselves) as a **Java library**.

The first targets are:

- The three non-biquad filters: **SVF** (Chamberlin state-variable),
  **Ladder** (Zavalishin TPT 4-pole Moog), and **OTA Ladder** (per-stage
  nonlinearity).
- The **supersaw / HyperSaw** oscillator (9 detuned PolyBLEP saw copies).
- The **BBD delay line** and the **vintage chorus** and **vintage echo** built
  on it.
- The **Juno DCO** (`vdco`).
- **PolyOsc**, including its PolyBLEP and phase accumulators.

More may follow.

### The binding runtime constraint

Voltage Modular does not run our JVM — it embeds its own. Two facts decided by
the host, not by us, shape everything below:

1. **The runtime is the Java 11 instruction set.** Source may be authored with
   a newer JDK, but it must be compiled `--release 11`; Java 11 class files are
   the deliverable. Targeting JDK 17 bytecode would produce class files the host
   refuses to load.
2. **We cannot pass JVM launch flags or add modules.** The host embeds a
   "custom HotSpot" runtime (JNI Invocation API, runtime JIT — confirmed by
   Cherry Audio's description of converting module bytecode to native at load
   time; this rules out AOT/native-image). We do not control its command line.

Together these eliminate **explicit SIMD via the Vector API**
(`jdk.incubator.vector`): it is an incubator module absent from the default
module graph, requires `--add-modules` at runtime, and does not exist at all on
Java 11. The only available vectorisation lever is **C2 autovectorization**
(SuperWord / SLP) inside the host JIT.

### What the Rust source already tells us

The Rust DSP uses **no explicit SIMD** — no `std::simd`, no `portable_simd`.
Its performance comes from a Structure-of-Arrays layout (`[f32; 16]` per-voice
state) and fixed-trip loops that LLVM autovectorizes. The porting *shape* is
therefore already correct. The risk is that **C2's autovectorizer is weaker
than LLVM's**, and the host's Java 11 C2 weaker still: it bails on loop bodies
containing branches, calls (`tanh`), table lookups, or unsigned-integer phase
tricks — several of which appear in exactly the kernels we most want fast.

The vectorisable axis is **voices / oscillator copies, never time**: every
filter and the BBD/echo are IIR with per-sample feedback, serial along the
time axis. An open question (see Consequences) is whether the host even exposes
that voice axis to a module, or hands voices one at a time.

## Decision

Build `patches-libs-jdk` as a Java 11-targeted library that ports selected
`patches` DSP kernels, prioritising **provable numerical fidelity to the Rust
originals** over premature vectorisation.

### Goals

1. **Fidelity.** Each ported kernel reproduces its Rust counterpart's output to
   a tight tolerance (≈1e-6) on shared golden vectors.
2. **Portability to the host.** Deliverables are Java 11 bytecode that loads and
   runs in Voltage Modular's embedded runtime with no launch-flag dependencies.
3. **Honest performance.** Reach for vectorisation only where profiling shows it
   matters *and* the kernel's shape lets C2 autovectorize. Treat recursive
   per-sample kernels as scalar-bound and optimise them as scalar code.
4. **Maintainability.** Keep the Java port close enough to the Rust structure
   (SoA layout, kernel boundaries) that the two can be cross-checked and kept in
   step as the Rust evolves.

### Non-goals

- Explicit SIMD / Vector API usage (impossible on this target).
- AOT / native-image (incompatible with the host's load-time JIT model).
- A general-purpose Java DSP framework. This is a curated port of specific
  kernels, not a re-architecture.
- Bit-exact reproduction where the platforms legitimately differ (e.g. `tanh`,
  transcendental rounding); fidelity is to tolerance, not to the last ULP.

### Methodology

1. **Scalar port first.** Translate each kernel straight, with plain `float[]`
   SoA state mirroring the Rust layout. No vectorisation intent at this stage.
2. **Golden-vector parity harness.** For each kernel, dump input→output vectors
   from the isolated Rust kernel in `patches-dsp` / `patches-vintage`, and assert
   the Java port matches within tolerance. This harness is the project's
   backbone and gates every kernel.
3. **Profile in-host.** Identify what is actually hot once running inside (or
   against a faithful replica of) the host runtime. Expect the recursive
   per-voice filters and BBD to dominate — and to be scalar-bound.
4. **Selective vectorisation.** Only for kernels that are both hot and
   data-parallel (phase accumulators, supersaw copy-sum, the linear SVF mix),
   rewrite the inner math branch-free and **verify autovectorization actually
   happened** against the host's compiler, not a newer JDK's.
5. **Verification tooling.** Use `-XX:+UnlockDiagnosticVMOptions
   -XX:+PrintAssembly` (with hsdis) and `-XX:+TraceSuperWord` to confirm AVX
   packing, and JMH for end-to-end throughput. Run these on an OpenJDK build
   **matched to the host's** (pin the exact OpenJDK 11 version the host embeds;
   if it cannot be pinned, also test under GraalVM CE 11 and assume the worse
   result), because C2's SuperWord behaviour changed materially across releases.

## Consequences

- The deliverable is constrained to Java 11 bytecode; tooling and CI must
  enforce `--release 11`.
- Vectorisation is a *contingent optimisation*, not a headline feature. Most of
  the value is a correct, well-tested DSP library that outclasses the stock
  Voltage modules; the SIMD ambition is realistically reachable only for a
  handful of branch-free, data-parallel kernels.
- **Open question to resolve early:** Voltage Modular's polyphony model. If a
  module is handed one voice at a time, the 16-wide voice axis disappears at the
  host boundary and the only remaining data-parallel axes are the supersaw's 9
  copies and per-block non-recursive work. This determines whether cross-voice
  vectorisation is even on the table and should be answered before any
  vectorisation work is scheduled.
- Keeping the Java port structurally close to the Rust creates an ongoing
  synchronisation obligation as the upstream DSP changes; the golden-vector
  harness is what makes that obligation tractable.
