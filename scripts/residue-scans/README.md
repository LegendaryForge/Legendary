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

## Why two calibrations, always

The height profile in `2026-08-28-graded-nexuses-design.md` §3 uses `restore` and `maxPitch` and
**states no value for either** — the document admits the gap itself. The two argument sets above
bracket its reported `p5..p95` crossing-height span of −155..+158; neither reproduces it, so the
true pair is a third value nobody wrote down.

Any `|Δy|`-derived figure is therefore calibration-dependent and must be reported for both until the
height profile is implemented with its constants stated. Structural results — which mechanisms work,
where the multi-element pile-up lives, that Euclidean attenuation cannot break the symmetry between
two crossing arms — hold in both.

## Deliberately self-contained

`N9Scan.java` reimplements `ResidueRandom`, `GrandConvergence` and the arm walk rather than calling
`:core`, because all three are package-private, and it adds the height profile, which is **designed
but not implemented**. That duplication is the cost of measuring a model that does not exist yet.

It does mirror `:core` on one point that matters: **element identity is included** (per PR #91).
Without it all six elements are one star and every cross-element figure is meaningless.

> **When `:core` changes, this does not follow.** It is a snapshot of a model at a moment. Re-derive
> before trusting it against a `:core` that has moved — the `SCAN_VERDICT` line says the run
> completed, not that it still describes the code.
