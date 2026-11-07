# Measurement window — how arbitrary is anchor selection, measured

**Status: MEASURED 2026-11-07** (session 30) against game build `0.7.6`, headless, no client.

Addresses the open `measurementRadius` decision that
[`2026-10-23-residue-sizing-re-derivation.md`](2026-10-23-residue-sizing-re-derivation.md) §5 left as
*"the live follow-up"*, and that
[`2026-10-22-residue-sizing-decision.md`](2026-10-22-residue-sizing-decision.md) §10 gated on
*"Unknown until walked to."*

**It does not close that decision.** It measures one element of five, favourably, and the element the
original argument was actually about is still unmeasured and predicted to go the other way. §"What
this does not settle" is the load-bearing section.

---

## Why this was measurable without a client at all

The decision was recorded as waiting on perception — walk to an anchor, see whether it feels
arbitrary. That framing bundles two questions, and only one of them needs a human:

1. **Is selection actually turning on differences too small to matter?** A quantity. Measurable.
2. **Does standing next to the result feel arbitrary?** A judgement. Needs a person.

(1) was blocked only by `TerrainSample`'s live adapter: `SectionReaderTerrainSample` gates every read
on `hasStorageInMemory`, so nothing can be measured outside chunks a connected client has loaded.
`ChunkGenerator.generateHeight(seed, x, z)` is a pure generator call that answers at any coordinate
with nothing loaded — measured against the topmost solid block **7/7 exactly** on `0.7.4` by
`/worldgenheight`. `GeneratorTerrainSample` wraps it, and `/residueseparation` is the probe.

### Earth only, and that is the predicates' doing rather than a shortcut

| Element | Headless? | Why |
|---|---|---|
| **Earth** | **yes** | `Geometric`, `SOLID`. Environment set is `Set.of("Env_")` and sky band is `0..15`, so **both gates are wide open** and only occupancy discriminates. The synthetic constants supplied for them cannot change the number |
| Wind | no | same wide gates, but `occupied(OPEN)` is `!solid && fluidAt().isEmpty()`. With no fluid data every ocean column above the seabed counts as open air, so Wind would read strongest over deep water |
| Water, Fire | no | `Physical`, keyed entirely on `fluidAt`. Strength is identically zero, indistinguishable from absent |
| Lightning | no | `Weather`, which `VolumeStrength` throws for, and which has no measure by any route |

**Proxy, stated before the numbers:** `isSolidAt` is `y <= height`, so caves and overhangs count as
rock. What is measured is therefore **surface relief**, not rock volume. For a question about whether
neighbouring windows see too much shared terrain, relief is the right signal; for Earth's absolute
strength it would not be.

## The predictions, written before the run

`AnchorSelector.beats` consults the position hash **only** when two strengths are integer-equal. So
two different things can be called arbitrary and they were predicted to move in opposite directions:

1. **Exact-tie rate** — literally "how often does the hash decide". Narrowing shrinks the integer
   range (43,560 possible values at radius 128, 11,560 at 64), so by pigeonhole it should go **up**.
2. **Relative separation** — how decisively terrain decides when it does. Narrowing reduces overlap
   between neighbouring windows, so it should go **up** too.

Both predictions held. Neither turned out to be the interesting number.

## The measurement

`/residueseparation`, seed `1787872410574`, region ±768 blocks, 144 candidates, `cellSize = 128`,
`suppressionRadius = 256`, Earth's floor 223,232 blocks = 436 samples at step 8. A contest is any
pair of floor-clearing candidates within the suppression radius; 683 of them, identical at both
windows.

### Control first

| window | eligible | contests | exact tie % | near tie % | median sep % | max strength |
|---|---|---|---|---|---|---|
| flat r=128 | 144 | 683 | **100.00** | 0.00 | 0.00 | 22,869 |
| flat r=64 | 144 | 683 | **100.00** | 0.00 | 0.00 | 6,069 |

Flat ground ties every contest, which is the state where the hash decides everything. Had it not, the
instrument would have been measuring something other than terrain.

**It also cross-checks the arithmetic exactly.** Flat at `y = 160` is solid for 21 of the 40 sampled
levels, so the window's count must be `perAxis² × 21` — `33² × 21 = 22,869` and `17² × 21 = 6,069`.
Both match to the digit. The `generateHeight` call counts do too: 152,840 columns against 41,556 is
3.68×, against the window-area ratio `33²/17² = 3.77`.

### Real terrain

| window | eligible | contests | exact tie % | **near tie % (<1%)** | median sep % | max strength | columns |
|---|---|---|---|---|---|---|---|
| r=128 (current) | 144 | 683 | **0.00** | **32.36** | 2.13 | 20,997 | 152,840 |
| r=64 | 144 | 683 | 0.29 | **18.45** | 2.87 | 5,955 | 41,556 |

## What it says

**§10's literal worry does not occur.** *"Hash-selected anchors read as arbitrary on the ground"*
requires exact strength ties, and at the current window there are **none** — 0.00% of 683 contests.
The mechanism that section names is absent for Earth, and narrowing would *introduce* a little of it
(0.29%).

**The substance of the worry is real, and large.** At r=128, **32.4% of contests are decided by a gap
under 1%**. That is terrain deciding, not the hash — but on a margin no player could perceive, which
is the thing §10 cared about rather than the mechanism it named. Narrowing to 64 nearly halves it, to
18.5%, and raises median separation from 2.13% to 2.87%.

So **narrowing is supported, for a reason adjacent to the one originally given.** The right metric was
neither of the two predicted; it was the near-tie rate, which is in the same table because the probe
printed the distribution rather than a single statistic.

## What this does not settle

**The original argument against a narrow window was about water, and water is exactly what this
cannot measure.** `2026-10-22`'s §"The measurement radius was decided against a premise that then
changed" argued 128 on saturation grounds: *"A 129-block window saturates on anything from a decent
lake upward, so a lake and an ocean arm tie at the ceiling and the position hash picks between
them — which §2 rejects outright: 'The open ocean is the strongest water place there is.'"*

That mechanism runs **opposite** to what is measured here. For a saturating `Physical` predicate a
narrower window saturates *more often*, so ties go up. Earth does not saturate — its strength scales
with relief — so the favourable result above says nothing about Water, and the direction for Water is
predicted to be unfavourable.

**Net state of the decision:** one element measured and favourable; the element the argument was
about unmeasured and predicted adverse. That is a real advance on "Unknown until walked to" and it is
not a decision.

### What would close it

- **Water's saturation rate at both windows.** Needs `fluidAt` headless. A sea-level constant plus
  `generateHeight` would approximate ocean water (`height < y <= seaLevel`) — unverified, and it
  would not cover lakes above sea level. Otherwise it needs a client.
- **The perception half**, which no measurement replaces: stand next to an anchor and judge it.
  Cheaper now, because the mechanism question is answered and only taste is left.

## Also recorded

**`SectionReaderTerrainSample.surfaceHeightAt` returns a fixed plane**, and its javadoc said *"finding
the real heightmap accessor is the next probe's question."* `generateHeight` is that accessor and the
question was already answered two sessions ago. The javadoc is corrected to say so.

**The upgrade is deliberately not made**, and the reason is a correction to this session's own
premise. It was proposed on the belief that anchor strengths depend on that plane. They do not:
`VolumeStrength` walks the whole column and never calls `surfaceHeightAt`, as
`ResidueAnchorsCommand:120` already stated. Its only real consumer is `SiteScorer`, the weighted-sum
scorer `/residueprobe` still drives. So the change affects that probe and nothing in the volume model,
and it would need the generator and seed threaded through seven construction sites, five of which hold
neither. Left as a small known change rather than done on a wrong justification.

## Scope

- `mod/hytale` — `GeneratorTerrainSample`, `ResidueSeparationCommand`, registration + boot log,
  `SectionReaderTerrainSample` javadoc.
- `scripts/live-probes/drive-residueseparation.sh`.
- This spec.

Out of scope: changing `measurementRadius`, re-deriving any floor, and the `surfaceHeightAt` upgrade.
Nothing here changes a parameter; it measures one.
