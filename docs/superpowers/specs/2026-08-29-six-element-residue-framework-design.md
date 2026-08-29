# The six-element residue framework — essences, nexuses, and the Lightning null

**Status: structure decided, numbers measured, one capability open.** This extends
`2026-08-28-graded-nexuses-design.md` and `2026-08-28-residue-density-circle-peak-design.md`. It
does not supersede either: the height profile, nexus grading by `|Δy|`, and the density-peak
resolution all stand. It settles which six elements the framework serves, separates two ideas that
had been sharing one word, resolves N12, and records what the engine will and will not let us do —
measured by running it, not by reading signatures.

Only the Lightning questline (Stormseeker) is being built. The other five appear here as
**constraints on `:core`**, not as designs. The test for anything in this document is: *does
element #2 force a breaking change if we omit it?*

---

## 1. What this changes

| Prior | Now |
|---|---|
| "Six elements" = Earth, Fire, Frost, Poison, Sand, Storm — taken from the shipped Circle monuments | The six are the shipped **essences**: Fire, Ice, Life, Lightning, Void, Water |
| A Circle is a self-crossing of a current | A **nexus** is the self-crossing. A **Circle** is a man-made structure that may stand at one. |
| The saturated core (N12) is a legibility defect to fix | Legibility was never lost. The real problem is harvest competition, and Lightning's core is **null** — not by physics, but because it is the wounded element |
| Crystals need authoring | Nine crystal rock families ship, in four sizes, glowing, harvestable and craftable |
| World expression is "unblocked: particle emission works" | Block **and** prefab placement confirmed by execution, anywhere in the world, with no player present |

---

## 2. The six are the essences

The prior specs inherited their element list from the Elemental Circle monument prefabs. That was
the wrong source. Hytale ships **exactly six essences**, one model with six textures, all
`Uncommon`, each with authored description text:

| Essence | `Light.Color` | Description says the source is |
|---|---|---|
| Fire | `#310` | *"Crucible's artisans"* — a fallen civilisation; today it *"lingers in the creatures"* of the desolated volcanic regions |
| Ice | `#023` | The Outlanders, who *"rejected the flame and split from their brethren"* |
| Life | `#130` | Plant life, *"in abundance"* |
| Void | `#103` | Void creatures, *"inextricably bound"* |
| Water | `#103` | Fish — *"as water filters through their gills, the concentration grows"* |
| **Lightning** | `#320` | ***"the true source of such magic is nowhere to be found"*** |

**Five of six name a living, present source. Lightning is the only absence.** The premise is no
longer an inference from a missing monument; it is the single asymmetry in a shipped set of six
parallel texts. Fire even supplies the precedent — a civilisation of artisans, and a desolation.

### The premise, restated on a like-for-like comparison

The narrative spec argued *"Storm alone has an essence and a spirit and no Elemental Circle"* by
comparing across two taxonomies that do not align. It can now be made on one axis. Four elemental
**spirits** ship, and each drops exactly one essence:

| Spirit | Drops | Monument family ships? |
|---|---|---|
| `Spirit_Ember` | `Ingredient_Fire_Essence` | Fire — yes (14 prefabs) |
| `Spirit_Frost` | `Ingredient_Ice_Essence` | Frost — yes (31) |
| `Spirit_Root` | `Ingredient_Life_Essence` | Earth / Druid Circles — yes (79), *by association* |
| **`Spirit_Thunder`** | **`Ingredient_Lightning_Essence`** | **none** |

Of the four elements whose elementals still wander, three have monuments and **Lightning alone has
none**. The Root↔Earth link is an association (root, druid, plant, Life), not an identity, and
should be written that way wherever it is used.

> **Do not re-derive the element list from the monument prefabs.** They are a different set (Earth 79,
> Sand 37, Frost 31, Poison 14, Fire 14 — 175 prefabs) that overlaps the essences on Fire only, with Frost/Ice being one idea
> under two names. Sand and Poison have no essence; Void, Water and Life have no monument.

---

## 3. Nexus and Circle are two different things

One word had been doing two jobs. Split it:

- A **nexus** is a plan-view self-crossing of one element's current. It is natural, computed, and
  graded by the vertical separation `|Δy|` of the two currents there. Nobody built it.
- A **Circle** is a **man-made structure** that may stand at a nexus. Peoples build them; the
  shipped monument families are the evidence that the practice exists.

The taxonomy generalises one level up, which is the sign it is the right cut: **nexus is to Circle
as Grand Convergence is to the ancients' workshop.** Natural feature, built structure, in both
cases.

### What this forces in `:core`

A rename, cheap now because the questline framework is placed but not adopted — the only callers
are its own tests:

| Now | Becomes |
|---|---|
| `ResidueNetwork.circlesWithin(...)` | `nexusesWithin(...)` |
| `CurrentParameters.circleRadius` | `nexusRadius` |
| `CurrentParameters.circleWeight` | `nexusWeight` |

### And it breaks a constant's provenance

`2026-08-28-residue-density-circle-peak-design.md` §9 sets `circleRadius: 12.0` *"from the beacons'
`BeaconRadius: 10` / `SpawnRadius: 12`"* — the footprint of a **man-made** Elemental Circle, used to
size the density peak of a **natural** feature. Under this split that reasoning no longer holds.

**The value may well survive; the justification does not.** Either re-derive `nexusRadius` from
something about the currents themselves, or keep 12.0 and record it honestly as a provisional
number with no derivation. Do not leave the beacon sentence in place — it now reads as a derivation
and is not one.

### What we cannot claim

We cannot make shipped monuments coincide with nexuses **at world-generation time**. That is
deferral #18, and it is the only thing #18 constrains here.

We *can* place a structure at a computed nexus at runtime, anywhere, with no player nearby (§9). So
the workshop at the Grand Convergence — 512–2048 m from origin — is reachable. What it cannot be is
*pre-generated*: it materialises when we place it, not when the world is made.

That is the ordinary worldgen contract restated one layer up. Vanilla knows deterministically what
a chunk will contain and materialises it on load; our network is the deterministic part and a
`ChunkPreLoadProcessEvent` hook is the materialisation. The one difference to design for: real
worldgen writes a chunk once, at generation, whereas a load hook fires on every load — so the write
must be idempotent. Read the world for a sentinel and skip if the structure is already there, which
also honours *state lives in placed objects, never in the field*.

---

## 4. Element identity in `:core`

Unchanged from `2026-08-28-graded-nexuses-design.md` §5 and still unimplemented: element identity
becomes a first-class constructor argument on `DefaultResidueNetwork`, mixed into the arm-jitter
domain and into a per-element heading offset derived from `(worldSeed, elementId)`.

This is the one item in this document that **must** land before element #2, because without it two
elements sharing a seed and tuning produce byte-identical networks, and the fix afterwards is a
breaking constructor change to a `:core` type several questlines already consume.

`GrandConvergence.locate` keeps its existing prohibition on per-element variation. Elements must
agree on the shared anchor; the inverse hazard — agreeing where they must differ — is what element
identity closes.

---

## 5. Crystals: the expression layer is almost entirely shipped

Reading `Rock_Crystal_Yellow_Medium.json` in full, the shipped crystal blocks already do what Class
C needs:

- **They glow.** `Light.Color: "#da6"`, plus `ParticleColor` and a `BlockParticleSetId`. The
  literacy signal renders itself.
- **They are harvestable.** `Gathering.Breaking.DropList`, `GatherType: Rocks`.
- **They are already craftable.** `Recipe: 4x Ingredient_Crystal_Yellow` at a Builders bench —
  cultivation has a shipped precedent, so R3 needs no new mechanism.
- **They come in four sizes** — `_Small`, `_Medium`, `_Large`, `_Block`. **Density becomes content
  for free**: small out on a current, large at a nexus.

Nine families ship: Blue, Cyan, Green, **Iridescent**, Pink, Purple, Red, White, Yellow.
(`Iridescent` has no `_Block` variant.)

### Essence → crystal rock

| Essence | Crystal rock | Corroboration |
|---|---|---|
| Fire | `Rock_Crystal_Red` | `Golem_Crystal_Flame` drops Red |
| Ice | `Rock_Crystal_Blue` | `Golem_Crystal_Frost` drops Blue |
| **Lightning** | **`Rock_Crystal_Yellow`** | `Golem_Crystal_Thunder` drops Yellow |
| Life | `Rock_Crystal_Green` | `Golem_Crystal_Earth` drops Green |
| Void | `Rock_Crystal_Purple` | no golem; Purple is unclaimed. Its `#103` light is **byte-identical to Water's**, so the colour corroborates nothing — this is a choice, not a derivation |
| Water | `Rock_Crystal_Water` | its own family |

Leaving **White**, **Iridescent** and **Pink** unclaimed by any element.

> **Tripwire: rock name is not shard name.** `Rock_Crystal_Water` drops `Ingredient_Crystal_Blue`,
> and `Rock_Crystal_Blue` drops `Ingredient_Crystal_Cyan`. Any code that infers the shard from the
> rock name is wrong for those two.

---

## 6. N12 resolved — the Lightning null

### The defect was misdiagnosed

`2026-08-28-graded-nexuses-design.md` §6 states the core problem as *"everywhere reads as
on-current, so there is no gradient to walk up."* That is a statement about the **union of six
elements' influence footprints**, and it is not what a player reads.

`densityAt` is an instance method on `ResidueNetwork`, and after §5 six elements are six independent
network objects with six independent density fields. Elements are visually distinct (their own
crystal colour, §5). For the one element a player is reading, the same measurement gives **30.5%
coverage within 500 m, 9.4% at 500–1500 m, 0.9% beyond** — a clean 3× gradient rising toward the
convergence, exactly what the two-mechanism navigation design wants.

**The legibility half of N12 dissolves. The harvest half survives and sharpens**: Lightning crystals
sit on roughly 34× more ground near the convergence than in the far field, so gathering at the
centre out-competes prospecting for a good nexus — the concern §6 flagged as load-bearing.

### The resolution

**Residue density for Lightning falls to zero near the Grand Convergence**, rising to normal at the
null radius.

This is **not** physics and **not** element-neutral. It is Lightning's signature, because Lightning
is the element whose source *"is nowhere to be found."* The other five elements keep rich cores. A
player approaching the convergence sees five colours intensifying around them while yellow thins to
nothing — **the wound is legible only because the crowd is there.** The saturated core stops being a
defect and becomes the contrast that makes the absence readable.

Consequences that follow from it being Lightning's, not physics':

- It lives in **`:quests:stormseeker`**, not `:core`. `:core` holds frameworks; a lore fact about one
  element is a use of the framework. Each future questline decides whether its own element is whole.
- The needle keeps working, and gains its best moment. Literacy teaches *more crystals, more power*
  for two acts; then the needle points upstream and the crystals die out. The instrument says
  forward, the land says nothing lives here. No text required.
- Density can no longer identify a nexus globally. See §6.3.

### The measurement

Two scans over 500 seeds at the `6×240` target, `.scratch/n12-scan/`. The 2D geometry ran against
the shipped `:core`; the height profile is designed but unimplemented, so it was reconstructed —
and §3 of the graded-nexus spec gives the formula but never states `restore` or `maxPitch`, so two
calibrations were run to bracket the spec's reported −155..+158 crossing-height span:

- **A** — `restore 0.020`, `maxPitch 0.35` → span −105..+107
- **B** — `restore 0.010`, `maxPitch 0.50` → span −200..+201

Every structural conclusion holds in both.

**Nexuses cluster near the convergence, contrary to expectation.** Adjacent arms leave only 60°
apart and `headingJitter` bends them together before they separate:

| | value |
|---|---|
| Crossing distance from convergence — p10 / p25 / median | **194 m / 370 m / 679 m** |
| Median distance of each world's *nearest* crossing | **195 m** |
| Crossings per world / zero-crossing worlds | 10.7 / **0 of 500** |

**And near nexuses are the better ones** — median `|Δy|` by band (calibration A):

| 0–100 m | 100–200 m | 200–300 m | 300–500 m | 500–1000 m | 1000 m+ |
|---|---|---|---|---|---|
| 35.7 | **30.7** | 48.4 | 64.3 | 66.2 | 68.1 |

**But the world's best nexus is usually far out anyway**, because there are ~10× more crossings out
there and the minimum over a large sample of mediocre crossings beats the minimum over a small
sample of good ones:

| null R | nexuses lost | median best `\|Δy\|` (A / B) | worlds left with **no** nexus |
|---|---|---|---|
| 0 | — | 7.3 / 12.3 | 0 |
| **200 m** | **10.5%** | **9.5 / 16.8** | **0** |
| 300 m | 18.4% | 10.6 / 20.2 | **0** |
| 400 m | 27.6% | 11.9 / 24.7 | 1 (0.20%) |
| 500 m | 35.8% | 13.5 / 26.8 | 2 (0.40%) |

That last column is height-independent and came out identically from two independently written
scans.

**Decision: `nullRadius = 200`.** Free of N8 — no world is left without a nexus — while removing the
worst of the harvest competition. 300 m remains available if play data says the core is still too
rich. **400 m and beyond reintroduces N8** and is not available at any tuning.

### The N6 interaction, named because nobody owns it otherwise

N6 established that density is `1.0` only at a nexus. Suppressing density inside `nullRadius` means
nexuses in that region no longer read `1.0`. *"Walk uphill"* survives — they remain **local** maxima
— but *"density 1.0 identifies a nexus"* stops being globally true.

This is a property spanning two specs, which is the exact shape of the
`2026-08-27 SMELL: a design property that spans sub-projects has no reviewer`. **The task that
implements `nullRadius` owns proving this**, with a test asserting a nexus inside the null radius is
still a local maximum of its neighbourhood.

---

## 7. The Grand Convergence: an inert memory

The convergence is the zero-separation limit nexus and the ancients' workshop stands on it. Under
the Lightning null it is also the emptiest place in the world for yellow crystal.

**What stands there instead is white — or iridescent — crystal that no longer grows.** Not a
resource. Inert, unharvestable, the residue of what the six currents made together before the
builders spent it. The recombination image is exactly right for what the convergence *was*; it goes
wrong the moment the player can pick it up, because that hands them the perfect nexus the whole
design says was already taken.

`Rock_Crystal_White` and `Rock_Crystal_Iridescent` both ship and neither is claimed by an element.
**Iridescent is the better literal fit** for many currents recombining; White is the better fit for
something bleached and spent. Open, §11.

---

## 8. Cross-element crossings (N9) — pushed outward

The prior spec excluded cross-element crossings *"until someone can name what happens at one"*, and
noted that with six elements on one convergence they would be common near the centre, reintroducing
the quadratic term the exclusion was written to remove.

**The Lightning null makes them affordable.** Near-centre crossings sit in dead ground for the one
element that reads them, so only the sparse outer ones matter. A crossing of two *different*
elements is a natural candidate for a nexus worth more than an ordinary one, and it is **pairwise**
— fifteen combinations for six elements, bounded and authorable — not one mystical all-six event.

Still deliberately open. What changed is that the objection to opening it has been removed.

---

## 9. What the engine actually allows

Verified by execution on **0.6.1** (`Implementation-Revision-Id: 5097cd9e`), headless, this session.
Commits `5b63dfd` and `026ade4`.

| Capability | Status |
|---|---|
| `setBlock` / `getBlock` / `getBlockType` / `breakBlock` | **Confirmed.** Five shipped crystal rocks placed and read back by exact string id |
| Placement persists across a full server restart | **Confirmed** across two boots |
| Prefab lookup, including the 171 dormant monument prefabs | **Confirmed.** Resolves against a zip-backed FileSystem |
| `PrefabBufferUtil.loadBuffer` | **Confirmed** |
| `PrefabUtil.paste` writes blocks | **Confirmed near spawn, partial** |
| Paste at arbitrary far-from-player coordinates | **Confirmed** — 862-block prefab written in full at 600 m with no player and chunks unloaded |
| `ChunkPreLoadProcessEvent` (+ section / save / unload) | Present; the natural materialisation hook |
| World particles at arbitrary positions | Confirmed 2026-08-27 (N3) |

Three things to carry into implementation:

**`setBlock` is a raw write, not a validated place.** It overwrote solid ground with no support
check, and 0.6.1 has no `placeBlock` or `testPlaceBlock`. The `Support.Down: Full` rule the block
asset declares is ours to enforce.

**Use the 8-arg `paste` overload, with `(1, 4)`.** `PrefabUtil.paste` is overloaded, and the
overloads do not behave alike. The 6-arg form — `(buffer, world, pos, rotation, random, store)` —
writes only into chunks already resident and skips the rest without error. Measured at one distance
in one run: the 862-block Fire pillar wrote **0** cells through it while the 27-block Druid circle
wrote all **27**, the small prefab fitting inside chunks a neighbouring paste had just loaded. The
8-arg form `(…, 1, 4, store)` — the arguments the shipped `PastePrefabEffect` passes, read from its
bytecode — wrote **867** and **27** at the same sites, and the `PasteRegion` overload wrote **870**
and **27**. `PasteRegion` is therefore optional, not required.

> **This corrects a false finding.** An earlier version of this section stated that `paste` is
> "silently bounded by chunk residency" and that remote placement was unavailable, citing eight
> sites where a prefab wrote 0. Every one of those used the 6-arg overload. The claim was built on
> null results plus an inference from the *existence* of `loadPasteRegionAsync` — reasoning from API
> shape to runtime behaviour, which is the exact error the rule below exists to prevent. Commit
> `026ade4`'s message carries the same wrong claim and stands as history.

**Prefab names are the path under `Server/Prefabs/` with the `.prefab.json` suffix.** The bare name
and a `Server/Prefabs/`-prefixed form both return `null`.

### The capability audit cannot be trusted per row

`docs/integration/hytale-capability-audit.md:36` describes block access as
`world.accessor.BlockAccessor` with `placeBlock`, `testPlaceBlock` and eight `setBlock` overloads.
**That class does not exist in 0.6.1.** The API lives on `IChunkAccessorSync`, which `World`
implements, with two `setBlock` overloads and neither of the other two methods. The audit was taken
against 0.5.9 and says of itself that it expires on every launcher update. `DynamicLight` and
`ParticleUtil` were spot-checked and are still present, so it is not uniformly wrong — it is
unreliable per row.

**Standing rule going forward: signature inspection is a hypothesis; a probe executed on the current
build is confirmation.**

---

## 10. Corrections owed to prior specs

Each of these is a sentence in a merged document that is now wrong. They should be amended together
with this spec, or the next reader inherits the contradiction.

1. **`2026-08-27-residue-network-design.md` §7** lists sub-project 3 as *"Unblocked: particle
   emission is available to plugins at arbitrary positions."* Crystals are blocks, not particles;
   the cited evidence never reached the claim. It is now unblocked for the right reason (§9).
2. **`2026-08-28-graded-nexuses-design.md` §6** states the saturated core has *"no gradient to walk
   up."* True of the six-element union, false for a per-element reader (§6).
3. **`2026-08-28-residue-density-circle-peak-design.md` §9** derives `circleRadius: 12.0` from
   Elemental Circle beacon radii. That derivation does not survive the nexus/Circle split (§3).
4. **`2026-08-28-graded-nexuses-design.md` §3** gives the height-profile formula without stating
   `restore` or `maxPitch`, so its own measurements cannot be reproduced. Record the two values.
5. **Everywhere the element list appears** as Earth/Fire/Frost/Poison/Sand/Storm, it is the monument
   set, not the essences (§2).

---

## 11. Open

- **N1** — final crossing count against play data. Target `6×240` unchanged.
- **N7** — the Z-filter regression hole. Untouched.
- **N8** — downgraded, and `nullRadius = 200` keeps it closed at 0 of 500 seeds. Re-measure the
  residual at the chosen parameters over a larger sample.
- **N9** — what happens at a cross-element crossing. Now affordable to answer (§8).
- **N10** — is residue a regional feature by decision?
- **N11** — the harvest and restoration economy. Its own spec.
- **N12** — **resolved** (§6).
- **N13 (new)** — White or Iridescent at the convergence (§7).
- **~~N14~~ — closed 2026-08-29.** Remote prefab placement works; `PasteRegion` is optional. Use
  the 8-arg overload (§9).
- **N16 (new)** — which materialisation strategy for the workshop and Act IV's Circle: a
  `ChunkPreLoadProcessEvent` hook (vanilla-like, arrives before the chunk is visible, must be
  idempotent) or an explicit one-shot placement. Capability is not the constraint; this is a design
  choice.
- **N15 (new)** — `nexusRadius` needs a real derivation or an honest "provisional" label (§3).

---

## 12. Provenance

Design conversation of 2026-08-29. Element taxonomy, spirit/monument comparison, crystal families
and block-type detail read from the installed `Assets.zip` (0.6.1). Nexus-distance and nexus-quality
figures from `.scratch/n12-scan/`, 500 seeds, two height calibrations. Capability findings from
`/blockprobe` (`5b63dfd`) and `/prefabprobe` (`026ade4`), executed headless against a live 0.6.1
server.

The two decisions that shaped it — *the six are the essences* and *nexus and Circle are different
things* — are the operator's, taken 2026-08-29.
