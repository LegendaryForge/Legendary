# Residue sizing — §9's open numbers, decided

**Status: DECIDED 2026-10-22.** Closes items 1, 2, 4 and 5 of
[`2026-10-21-residue-anchor-density-design.md`](2026-10-21-residue-anchor-density-design.md) §9, and
strikes item 3 as not a decision. Operator judgements taken in session 25; the arithmetic linking them
is derived here and reproducible.

Nothing in the anchor-density spec is implemented. This document supplies the numbers that gated it;
it does not change the model.

---

## The decisions

| § 9 | Parameter | Value | Kind |
|---|---|---|---|
| 5 | Influence radius `w` | **8 blocks** | operator judgement |
| 2 | Measurement radius | **128 blocks** (257-block window) | operator judgement |
| 1 | Suppression radius `R`, all five elements | **512 blocks** (baseline vector) | derived from `w` + coverage |
| 4 | Crossing tolerance `D` | **8 blocks** (`= w`) | derived, bounded |
| 3 | Crossing frequency | **not decided — deferred measurement** | see below |

**Coverage target: ~15% of the world near some current**, all five elements, worst case. That is the
operator judgement the suppression radius derives from, and it is explicitly provisional — *"we'll
have to see what that ends up looking in-game."*

---

## Why these, and what is derived from what

### `w` and the coverage target are the only free choices

§3 establishes that the covered fraction is `2w / R` per element, independent of world size, anchor
count and the element's abundance. Across five elements the worst case is `10w / R`, so a coverage
target `C` fixes the suppression radius:

> **`R = 10w / C`** — at `w = 8` and `C = 0.15`, `R = 533`, taken as **512** to stay on the power-of-two
> scale §3's table uses.

Everything else about the network's density follows. Per element, per square kilometre:

| Quantity | Value |
|---|---|
| anchors (`A/R²`) | **3.81** |
| total current length (`A/R`) | **1,953 m** |
| crystal-band area (`2w·A/R`) | 31,250 m² = **3.125%** |
| all five, worst case | **15.6%** |

So the picture at ground level: in a square kilometre, roughly four anchors each for water, earth,
wind and lightning — and four for fire **only if** volcanic terrain exists there — joined by currents
totalling about two kilometres per element, each wearing a 16-block crystal band. Walk a kilometre in
a straight line and you cross several bands.

`2w·A/R` is an **upper bound, not a figure**: §5 gates crystals on passing within `w` of earth, and
intersecting a bounded band with a gate can only shrink it. Open ocean and open sky produce no
crystals, so realised coverage is below 15.6% by however much of each current runs through water or
air.

### The measurement radius was decided against a premise that then changed

The recommendation of 128 blocks was argued at `R ≈ 1,024` (the `w = 16` branch), where a square
kilometre's worth of terrain competes for one anchor and the contest is therefore between a region's
*largest* features. A 129-block window saturates on anything from a decent lake upward, so a lake and
an ocean arm tie at the ceiling and the position hash picks between them — which §2 rejects outright:
*"The open ocean is the strongest water place there is."* A 257-block window keeps grading where the
contest happens.

**`w = 8` then halved `R` to 512, and the premise weakened.** The competition neighbourhood is now
~0.26 km² rather than ~1 km², so fewer 257-block-plus features fall inside one, and saturation ties
are correspondingly rarer. The recommendation survives — grading at the large end is still right, and
§2's position on open water is unchanged — but it rests on a smaller margin than when it was made.
Recorded because the figure was chosen under the wider `R` and a reader would otherwise reconstruct a
stronger argument than the one that applies.

**Known cost, accepted:** a 257-block window centred on a coastline is half land, so a shore site
scores about half an equivalent open-water site. §2 already rejected water-as-shoreline for ranking a
beach above the ocean, so this is the intended direction rather than a side effect.

**A second-order risk this creates, stated as a prediction.** The window (257 b) is now about half the
suppression radius (512 b), so two candidates a few hundred blocks apart see substantially overlapping
terrain and score nearly identically — leaving the hash to separate them. §10 already names *"hash-selected
anchors read as arbitrary on the ground"* as a thing that would change the decision; a window of
`R/2` makes that more likely than a window of `R/4` would. **Unknown until walked to.** If it reads
badly, §10's own remedy applies — give uniform regions no anchors — and the cheaper dial to try first
is a narrower window, not a cleverer measure.

### `D = w` is bounded, not tasteful

A geode forms where two elements' currents pass within `D` of each other **inside earth** (§5), and
crystals grow within `w` of a current. If `D > w`, two currents can qualify as crossing at a point
outside both crystal bands — a geode in ground showing nothing that explains it. So `D ≤ w` is a
correctness bound rather than a preference, and `D = w` is its natural limit: they met where a player
standing there can see both. **8 blocks.**

This is derived, so it is the first value to revisit if crossings turn out too rare — `D` is the only
one of these numbers that trades directly against crossing frequency.

### Item 3 is struck, not answered

§9 item 3 is **unmeasured and nothing gates on it**: *"too few means geodes are rare, which is
acceptable."* Choosing a value would invent a constraint the model does not have. It is a deferred
measurement with the trigger §9 already states — **measure it before anything required is placed in a
geode, rather than after.** Since the needle's recipe moved out of scope (§8), nothing required is
placed in one today.

### The per-element spread is a baseline, not five values

§9 item 1 asks for five radii and expects them to *"need iteration against real terrain rather than
derivation."* All five therefore start at **512**, which is a starting vector in the sense §3 means by
*"the starting frame, not an answer."* The expected direction of adjustment, from §3:

| Element | Qualifying terrain | Expected adjustment |
|---|---|---|
| Earth, Wind | nearly everywhere — rock below, open air above | **wider** than 512; saturate first |
| Fire | volcanic regions only | **narrower**, or unchanged with sparse coverage accepted |
| Water | coasts, lakes, rivers, ocean | near baseline |
| Lightning | storm-prone exposed terrain | **narrower** if the empty case bites — Stormseeker needs it |

Note what per-element radii are compensating for, because §3's wording invites a misreading. Abundance
has no say in anchor *count* for regions **of equal area** — an ocean yields what an equal area of
varied coastline yields. What differs per element is the *extent of qualifying terrain at all*, since
a region with none gets no anchor (§3's third regime). Fire's radius is small because volcanic terrain
is rare, not because lava is sparse within it.

---

## What would change this

- **The coverage target is provisional by the operator's own framing.** 15% was chosen to be looked at
  in-game, not defended. Every number here except `D` moves with it, and the relation `R = 10w/C` makes
  the move mechanical.
- **Lightning's empty case fires.** If storm-prone terrain is patchy enough that regions get no
  Lightning anchor, Stormseeker is affected and Lightning's radius narrows ahead of the others. This is
  the first thing to check on a live run.
- **Hash arbitrariness reads badly on the ground**, per the prediction above. Try a narrower
  measurement window before anything else.
- **Crossings turn out vanishingly rare.** `D` is the dial, and it is the one value here that was
  derived rather than chosen — so raising it means re-examining whether a geode outside both crystal
  bands is acceptable after all.

## Out of scope, and flagged rather than decided

**`verticalSpan` is not among §9's five numbers and is now load-bearing.** Today it is a single value
(16 blocks, giving ±16 around the anchor), but §4 gives each element its own y — fluid surface for
Fire and Water, below ground for Earth, above terrain for Wind, exposed surface for Lightning. A
257-block-wide window only 33 blocks tall is a flat slab, which suits a fluid surface and suits
Earth's *"the cave volume's own level"* much less. Under §2 the window's vertical extent is part of
what strength counts, so a per-element span is implied by §4 and has not been specified. It needs its
own decision before implementation.

Cost is not a factor in it: the chosen window is **5,445 reads per candidate** (33×33×5 at step 8),
**0.196 ms** at §6's measured ≈36 ns/read, or **4.9 ms** for a 25-candidate run. §6's conclusion holds
— *"cost does not constrain this design."*
