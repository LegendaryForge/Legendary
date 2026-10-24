# Residue scans

Standalone measurement probes for the residue design. **Not part of the build** — no Gradle module
includes this directory, and nothing here is compiled by `./gradlew build`.

## Why these are committed

They are the evidence base for committed design documents. `2026-08-30-n9-all-crossings-are-nexuses-design.md`
rests entirely on `N9Scan.java`, and `2026-10-23-residue-sizing-re-derivation.md` rests entirely on
`SizingScan.java`. A spec whose figures cannot be re-derived is a spec whose
figures have to be taken on trust. These lived in git-ignored `.scratch/` first; a helper cited by a
committed document has crossed the promotion threshold in
`Systems/Tooling/scratch_helpers_convention.md`.

## `SizingScan.java` — the anchor-density sizing chain

Executes what anchor-density design §3 estimates: candidate lattice → `AnchorSelector` retention →
§5's MST → crystal-band area as a rasterised union. Faithful to the implementation in the three
places that matter — the real `CandidateLattice` jitter, the real `ResidueRandom.mix` SplitMix64
tie-break, and `AnchorSelector`'s exact `> radius` skip (so equality still competes).

```bash
java SizingScan.java <suppressionRadius> <cellSize> <span> <trials> <label>
java SizingScan.java 512 256 8192 3 "as designed"   # the superseded values
java SizingScan.java 256 128 8192 3 "corrected"     # → out-sizing-corrected.txt
```

It found §3's coverage estimate high by ~2.1x and its anchor density high by π. Ends with
`SIZING_VERDICT: COMPLETE` — read that line by name.

**Each element gets an independent strength field**, which is what varied terrain supplies. That is a
modelling choice, and it is the regime the coverage target describes; the real `AnchorSelector` hash
carries no element term, so two elements that both tie across the same candidates would select
identical anchors. See the re-derivation spec §5.

## Running

Java 25, single file, no build step:

```bash
java N9Scan.java <armCount> <stepsPerArm> <seeds> <restore> <maxPitch> <label> [heightBand]
java N9Scan.java 6 240 40 0.020 0.35 "calibration A"   # → out-calibA.txt
java N9Scan.java 6 240 40 0.010 0.50 "calibration B"   # → out-calibB.txt
```

`heightBand` is optional and defaults to the published **96.0**, so every invocation
on this page reproduces unchanged. It was made settable on 2026-10-20 for the sweep
below.

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

## WORLD FIT — the engine constrains the height profile, and nothing knew

Added 2026-10-20. Hytale's chunk storage is `EnvironmentSection[10]` at 32 blocks per
section — `BlockChunk` allocates `new EnvironmentSection[10]`, and `EnvironmentSection`
reports `COLUMNS = 1024` with `LAST_RUN_MAX_Y = 31`. **The world is 320 blocks tall.**
That is a hard engine constraint, and no residue document had ever referenced one.

Measured against it, with relative `y = 0` anchored at mid-height:

| | full range | p1..p99 | outside a 320-block world |
|---|---|---|---|
| crossings | **−409 .. 433** | −224 .. 221 | **9.04%** |
| arm vertices | −417 .. 438 | −234 .. 233 | **11.41%** |

**The metric three specs validated against is what hid this.** `VALIDATION
crossing-height p5..p95` reports −157..154 — a tidy ~311-block band that looks like it
fills a 320-block world neatly. The realised range is 2.7× wider. A p5..p95 band cannot
see its own tails, and the tails are the whole question when the question is "does this
fit."

So the §8 calibration crisis was never unrecoverability. `restore` and `maxPitch` were
being fitted to a **remembered span from a deleted program**, which has no right answer
— hence six pairs tying and the choice having to be made by decision. Fitting arms
inside a real box is a well-posed problem instead.

## HEIGHT_BAND sweep — world fit and supply are the same lever

```bash
./sweep-heightband.sh [seeds]    # default 150  → out-heightband-sweep.txt
```

| band | world fit | supply @ `\|dy\|<24` | supply @ absorption 150 |
|---|---|---|---|
| **96** (shipped) | 8.81% outside | 1.72x | **1.03x** — §8's "wash" |
| 64 | 4.04% | 1.92x | 1.20x |
| 48 | 1.83% | 2.06x | 1.35x |
| **32** | **0.27%** | 2.34x | **1.60x** |

This answers a question `2026-08-30-n9-all-crossings-are-nexuses-design.md` §8 declared
a design dilemma: *"a world-level absorption radius of 150 and a 24-unit |dy| gate
cannot both stand if N9 is meant to increase supply."* **Both can stand.** The parameter
§8 never varied is `HEIGHT_BAND`, because nothing constrained the vertical extent, so
the one free parameter sat outside the search space while two fixed ones were traded
against each other.

One mechanism, both effects: a tighter band puts arms closer together in `y`, so fewer
crossings leave the world **and** more pairs pass the `|dy|` gate.

**32 is not a recommendation** — it is the end of the swept range, and it demonstrates
that the lever exists. The value wants fitting to a stated objective. Two consequences
to carry: a tighter band makes the `|dy|` gate *less* selective, and §4 justified that
gate with "a Fire arm at y=50 and an Ice arm at y=150 do not meet", a case that gets
rarer as the band narrows; and the `>=3`-element participation figures barely move
across the sweep, so the multi-element pile-up is **not** what pays for the extra
supply.

## Deliberately self-contained

`N9Scan.java` reimplements `ResidueRandom`, `GrandConvergence` and the arm walk rather than calling
`:core`, because all three are package-private, and it adds the height profile, which is **designed
but not implemented**. That duplication is the cost of measuring a model that does not exist yet.

It does mirror `:core` on one point that matters: **element identity is included** (per PR #91).
Without it all six elements are one star and every cross-element figure is meaningless.

**The height walk is mean-reverting, not clamped.** `HEIGHT_BAND` is the reversion scale
toward `y = 0`; only `pitch` is clamped, never `y`. So the name reads like a bound and is
not one, which is exactly why the realised excursion runs to ±433 at band 96. Anything
implementing this model in `:core` needs to decide what happens at the world's floor and
ceiling, because the model as specified does not say.

> **When `:core` changes, this does not follow.** It is a snapshot of a model at a moment. Re-derive
> before trusting it against a `:core` that has moved — the `SCAN_VERDICT` line says the run
> completed, not that it still describes the code.
