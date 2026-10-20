# Residue scans

Standalone measurement probes for the residue design. **Not part of the build** — no Gradle module
includes this directory, and nothing here is compiled by `./gradlew build`.

## Why these are committed

They are the evidence base for committed design documents. `2026-08-30-n9-all-crossings-are-nexuses-design.md`
rests entirely on `N9Scan.java`, and a spec whose figures cannot be re-derived is a spec whose
figures have to be taken on trust. These lived in git-ignored `.scratch/` first; a helper cited by a
committed document has crossed the promotion threshold in
`Systems/Tooling/scratch_helpers_convention.md`.

## Running

Java 25, single file, no build step:

```bash
java N9Scan.java <armCount> <stepsPerArm> <seeds> <restore> <maxPitch> <label>
java N9Scan.java 6 240 40 0.020 0.35 "calibration A"   # → out-calibA.txt
java N9Scan.java 6 240 40 0.010 0.50 "calibration B"   # → out-calibB.txt
```

Each run ends with `SCAN_VERDICT: COMPLETE`. **Read that line by name, not by position** — this
workspace has recorded five occasions where something appended output after a verdict and the reader
saw the wrapper instead.

## The calibration is now fixed — use `out-chosen.txt`

```bash
java N9Scan.java 6 240 300 0.012 0.44 "chosen"    # → out-chosen.txt
```

**`restore = 0.012`, `maxPitch = 0.44`**, set 2026-10-20 **by decision**, because the original pair
turned out to be unrecoverable. `out-calibA.txt` and `out-calibB.txt` are retained as the bracket
that led there, not as live calibrations.

Two reasons recovery failed, both established by sweeping this harness:

1. **One observable, two parameters, flat ridge.** `restore` and `maxPitch` trade off, so six pairs
   tie at the same error against the −155..+158 target and none reaches zero. Choosing on robustness
   — interior of the ridge rather than a grid edge — is what picked `(0.012, 0.44)`.
2. **The target came from a harness that no longer exists.** The −155..+158 span and the
   −105..+107 / −200..+201 bracket all trace to `.scratch/n12-scan/`, which was **git-ignored and is
   gone**. This harness is an independent reimplementation of the same unimplemented model and
   disagrees by about 5%: A measures −102..100 here, B measures −189..186.

> Note what that costs. Three numbers cited across three committed specs were produced by a
> measurement git never tracked, and they cannot now be checked or re-derived. This directory exists
> because of that lesson; see `Systems/Tooling/scratch_helpers_convention.md`.

Structural results were never calibration-dependent and still hold — which mechanisms work, where
the multi-element pile-up lives, and that Euclidean attenuation cannot break the symmetry between
two crossing arms.

## Deliberately self-contained

`N9Scan.java` reimplements `ResidueRandom`, `GrandConvergence` and the arm walk rather than calling
`:core`, because all three are package-private, and it adds the height profile, which is **designed
but not implemented**. That duplication is the cost of measuring a model that does not exist yet.

It does mirror `:core` on one point that matters: **element identity is included** (per PR #91).
Without it all six elements are one star and every cross-element figure is meaningless.

> **When `:core` changes, this does not follow.** It is a snapshot of a model at a moment. Re-derive
> before trusting it against a `:core` that has moved — the `SCAN_VERDICT` line says the run
> completed, not that it still describes the code.
