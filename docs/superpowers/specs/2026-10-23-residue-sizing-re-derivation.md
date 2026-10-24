# Residue sizing, re-derived — the correction is a scale halving

**Status: DECIDED 2026-10-23.** Supersedes
[`2026-10-22-residue-sizing-decision.md`](2026-10-22-residue-sizing-decision.md), which is bannered
rather than deleted. It keeps every *judgement* that document recorded; what changes is the
arithmetic linking them, which was wrong in a way that made the world about half as currented as the
operator asked for.

Nothing here changes the model in
[`2026-10-21-residue-anchor-density-design.md`](2026-10-21-residue-anchor-density-design.md). This
supplies corrected numbers for the dials it left open.

---

## The decision

| Parameter | Was | Now | Kind |
|---|---|---|---|
| Suppression radius `R` | 512 b | **256 b** | derived from `w` + coverage |
| `cellSize` | 256 b | **128 b** | derived — preserves `R/cellSize = 2` |
| Influence radius `w` | 8 b | **8 b** unchanged | operator judgement |
| Crossing tolerance `D` | 8 b | **8 b** unchanged | derived, bounded by `w` |
| Measurement radius | 128 b | **128 b** unchanged, but see §5 | operator judgement |

**Halve the two coupled lengths and leave the other three alone.** `w` is not scale-coupled — it is
what a player sees standing next to a current — and holding it fixed while `R` halves is precisely
the mechanism that doubles coverage.

---

## 1. What was wrong

§3 derives coverage through three steps:

> *"Anchors pack at about one per `R²` … so over area `A` there are `A/R²` of them. A minimum
> spanning tree gives one edge each, each roughly `R` long, so total current length is about `A/R`.
> Crystals grow within influence radius `w`, making the crystal-bearing band `2wA/R`."*

Step one is wrong, and it makes step two wrong as well.

`AnchorSelector` is a **Matérn type II hard-core process** — mark each candidate, retain it if
nothing within `R` outranks it. Each retained anchor excludes a **disc** of `πR²`, not a square of
`R²`, so density is `1/(πR²)`. That is the π that session 27 found.

Step two then fails for a reason that is easy to miss: *"each edge roughly `R` long"* is only
self-consistent when density is `1/R²`. At the true density the mean spacing is `√π·R`, so the edges
are longer — and the two errors partly cancel. **Coverage is overstated by ~2.1×, not by π.** Reading
the π factor as the coverage error is the trap this document exists to close, and it is why session
27's `R ≈ 289` is not the answer: 289 restores the *reported anchor count*, which was a derived
consequence. It does not restore the **coverage target**, which is the operator judgement.

---

## 2. What was measured, not estimated

[`../../../scripts/residue-scans/SizingScan.java`](../../../scripts/residue-scans/SizingScan.java)
executes the chain: real `CandidateLattice` jitter, the real `ResidueRandom.mix` SplitMix64 tie-break,
`AnchorSelector`'s exact retention rule, §5's MST, and the crystal band measured as a **rasterised
union** rather than assumed to be `2w × length`.

```bash
java SizingScan.java 512 256 8192 3 "as designed"
java SizingScan.java 256 128 8192 3 "corrected"
```

Written twice independently — once in Python, once here — and the two agree to within noise
(anchors 1.24 vs 1.23, MST 948 vs 946 m/km², per-element coverage 1.51% vs 1.51%). The committed
figures are this file's.

| Quantity, per element | Design says | R=512 measured | R=256 measured |
|---|---|---|---|
| anchors / km² | 3.81 | **1.23** (ratio 3.09 ≈ π) | **5.00** (ratio 3.05 ≈ π) |
| MST current / km² | 1,953 m | **946 m** (ratio 2.06) | **1,847 m** (ratio 2.11) |
| crystal-band coverage | 3.13% | **1.51%** (ratio 2.08) | **2.94%** (ratio 2.12) |
| five elements, 5× convention | 15.63% | 7.53% | 14.72% |
| **five elements, measured union** | — | **7.18%** | **13.70%** |

**The band formula itself is sound.** `2w·L` reproduces per-element coverage exactly (1.51% and
2.96%), so overlap within one element's own network is negligible. The whole error is in `L`.

### The target is a union, and the doc reported a 5× upper bound

The operator judgement is *"~15% of the world near **some** current"* — a union. The design computed
it as 5× the per-element figure, which assumes the five networks never overlap. Measured union at the
corrected parameters is **13.70%** against the 5× convention's 14.72%, so the convention overstates by
about 1.07× in this regime. Both are reported above; the union is the one to read.

### Scale invariance is what makes this a halving rather than a tuning

At fixed `R/cellSize`, every dimensionless property of the network is preserved and only the scale
changes. Measured across three scales at `R/cellSize = 2`:

| `R` | anchors / km² | per-element coverage |
|---|---|---|
| 512 | 1.23 | 1.51% |
| 256 | 5.00 (×4.07) | 2.94% (×1.95) |
| 128 | 19.99 (×4.00) | 5.75% (×1.96) |

Anchors scale as `1/R²`, coverage as `1/R`, to measurement precision. So the correction is a change
of scale, not of character.

---

## 3. The corrected relation

Replacing §3's `R = 10w / C`:

> **`R ≈ 4.5 w / C`** — at `w = 8` and union target `C = 0.15`, `R = 240`, taken as **256** to stay
> on the power-of-two scale §3's table uses.

This is the same rounding the superseded decision applied (it derived 533 and took 512). The constant
is empirical, fitted to the measurements above rather than derived in closed form, because the MST
constant for a hard-core process is not the Poisson one — measured `L ≈ 0.48 A/R` against the design's
`A/R`.

The exact-target value is `R ≈ 230` (measured union 15.39% at `R = 224`). **256 is chosen over 224**
because the coverage target is explicitly provisional — *"we'll have to see what that ends up looking
in-game"* — and 13.70% is inside "~15%", while a power-of-two radius keeps §3's table readable. If
the in-game look argues for more coverage, 224 is the next stop and the relation above makes the move
mechanical.

---

## 4. Why `cellSize` halves too, and why it is not optional

The superseded decision inherited a claim from session 27 that **`cellSize` has no effect on density**,
because `λₚπR² = 12.6` put the process deep in saturation. That is true at `R = 512, cellSize = 256`.
It stops being true if `R` halves alone:

| `R` | `cellSize` | `λₚπR²` | anchors / km² | saturated limit |
|---|---|---|---|---|
| 256 | 256 | 3.14 | 5.64 | 4.86 |
| 256 | 128 | 12.57 | 4.98 | 4.86 |
| 256 | 64 | 50.27 | 4.90 | 4.86 |

At `cellSize = 256` the process is **16% above** its saturated density and `cellSize` becomes a live
dial on a quantity it is not meant to control. Halving it restores saturation and with it the
property the design relies on: candidate spacing proposes, terrain disposes, and the lattice does not
set the count.

It also preserves the competitor count — `πR²/cellSize² − 1`, **11.6 at both the old and new
parameters**. That matters because §10's named risk is *"hash-selected anchors read as arbitrary on
the ground"*, and the number of contests a hash can decide is unchanged. **The superseded document
predicted the opposite** — that a window at `R/2` rather than `R/4` would make hash arbitrariness
*more* likely. The relevant ratio is window to **candidate spacing**, not window to `R`; shrinking
`R` alone reduces competitors and so reduces hash-decided contests.

**Cost:** 4× the candidates per km² (15.3 → 61). At session 27's measured 99–117 ms per step-8
candidate window, a km² goes from ~1.7 s to ~6.8 s of derivation. Acceptable for a run-ahead service;
worth knowing before anything derives on demand.

---

## 5. Out of scope, and flagged rather than decided

**The measurement radius is now coupled to the strength floors, and this is the live follow-up.**
`measurementRadius` stays 128 here, which leaves the window (257 b) twice the new candidate spacing
(128 b) rather than equal to it, so neighbouring candidates see more shared terrain than before. §10's
stated remedy for arbitrary-looking anchors is *"a narrower window"* — but halving it is **not** a free
change: `ElementGrounding.minimumStrengthSamples()` is calibrated against the current window
(`436 = 1%` of `33×33×40`), and Fire's `10` came from a live `0`-against-`44` separation measured at
radius 128. Shrinking the window changes what every floor means. Decide the two together or not at
all.

**`verticalSpan` is still unspecified** and the superseded document's §"Out of scope" applies
unchanged — §4 gives each element its own y while the span is a single global 16.

**The anchor hash carries no element term.** `AnchorSelector.positionHash` is
`mix(worldSeed, x, z)`, so wherever two elements each tie across the same candidates they select
**identical** anchors and their currents coincide — which, with `D = w`, would have §5 mint geodes
continuously rather than at points. The old `CurrentGeometry` has an `ELEMENT_DOMAIN` separator for
exactly this. The measurements above are unaffected: they give each element an independent strength
field, which is what varied terrain supplies, and that is the regime the coverage target describes.
Recorded here because it is adjacent; it belongs to the unimplemented crossing work.

---

## What would change this

- **The coverage target moves.** It is provisional by the operator's own framing. `R ≈ 4.5w/C` makes
  the move mechanical, and 224 is the next power-of-two-adjacent stop upward in coverage.
- **`w` moves.** Everything here except `D`'s bound scales with it.
- **The measurement radius is narrowed** per §5 — which forces the floors to be re-derived with it.
- **The MST is implemented and its real edge length differs from `0.48 A/R`.** §5 is unbuilt;
  `CurrentGeometry` is still the retired convergence-star code. The constant above is measured
  against the MST as specified, not as shipped.
