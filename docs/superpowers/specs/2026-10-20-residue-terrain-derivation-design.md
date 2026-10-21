# Residue network — terrain derivation, and the five-plus-one roster

**Status: PROPOSED DIRECTION. Not decided, not approved, nothing implemented.**

This records a design conversation on 2026-10-20 that arrived at a different model from the one three
committed specs describe. It is written down because the alternative was losing it to scrollback, not
because it has been accepted.

Read §11 before acting on any of it: this document would supersede substantial parts of
`2026-08-27-residue-network-design.md`, `2026-08-29-six-element-residue-framework-design.md` and
`2026-08-30-n9-all-crossings-are-nexuses-design.md`, and none of those have been retired.

**Provenance discipline.** Claims here are tagged: **[measured]** means executed against the shipped
build or computed from committed data; **[jar]** means read off `HytaleServer.jar` or `Assets.zip` with
no execution; **[lore]** means community-wiki sourced, which is the weakest tier here — see §12;
**[proposed]** means it came out of the conversation and rests on nothing yet.

---

## 1. Why the committed model needs replacing

The committed design is a **single-convergence star**: `GrandConvergence.locate(long worldSeed)` returns
one point per world, 512–2048 blocks from origin, and six arms per element walk outward from it as
seeded correlated random walks.

**Hytale is effectively unbounded horizontally. The star is not.** [measured] From the committed
`out-chosen.txt`, nexus density by distance from the convergence:

| band | today nexuses/km² | N9 gated/km² |
|---|---|---|
| 0–200 m | 53.48 | 436.03 |
| 200–500 m | 23.59 | 33.15 |
| 500–1000 m | 9.63 | 8.43 |
| 1000–2000 m | 1.75 | 1.48 |
| 2000–3000 m | **0.10** | **0.09** |
| 3000–4000 m | **0.00** | **0.00** |

A player who walks five kilometres from the convergence finds no residue, ever. The scan's own section
header concedes the frame — *"radial single-convergence model, NOT the N10 lattice."* N9 §10 records the
far-field decay as unsolved and hands it to N10, which was never built.

So the committed model is not a network spanning the world. It is one localised feature in an infinite
world, and every attempt to fix it by tuning (N9's cross-element pairs, absorption, the `|Δy|` gate) has
moved supply around inside the same disc.

**A second, independent problem.** [measured] The designed height profile does not fit the world.
Hytale's chunk storage is `EnvironmentSection[10]` at 32 blocks per section, so the world is **320
blocks tall**; the modelled excursion puts **9.04% of crossings and 11.41% of arm vertices outside it**
(full range −409..433). See `scripts/residue-scans/README.md` § WORLD FIT. The calibration those numbers
were fitted to came from a deleted, untracked harness, so it had no recoverable referent.

Both problems share a cause: **the network was designed as free-standing mathematics, with no reference
to the world it lives in.**

---

## 2. The proposal, in one paragraph

Stop generating the network and start **deriving** it from the world. Place nexuses at terrain sites
whose character already matches an element — fire at lava, water at rivers, earth in stone, wind at
exposed heights — then draw plausible lines between same-element nexuses. Density follows from distance
to those lines, as it does today. The network then exists wherever the world exists, because the world
is what generates it.

---

## 3. The roster is probably wrong, and that is a bigger finding than the derivation

`:core` currently keys residue to six elements taken from the shipped **essence items**: Fire, Ice,
Life, Lightning, Void, Water. [jar]

**Hytale's six elemental runes are Wind (Freedom), Earth, Lightning, Water, Fire, Void**, and *five are
connected to Gaia — all except the Void*. Water officially *"represents the magic of water and ice"*, so
ice is subsumed under Water rather than standing alone. [lore]

Against that, our roster is four right, with **Ice and Life that are not runes** and **Wind and Earth
missing**.

**Asset corroboration, and a correction.** `Server/Abilities/Elements/` ships exactly five: **Earth,
Fire, Lightning, Water, Wind** — the five Gaia runes, with Void absent because Void is not Gaia's and is
not a player element. [jar] Earlier the same day this was examined and dismissed in
`2026-10-20-hytale-0.7.2-capability-assessment-design.md` §5.8 as *"a damage-type list for combat"*
whose overlap with our essences was *"coincidental naming"*. The content reading was right — an element
there is `{"DamageCause": "Lightning"}` — but the **roster** reading was wrong: that is the rune list,
and it corroborates the lore independently of any wiki.

Our own setting brief had already flagged the hole it left: *"**There is no Air or Wind essence.**
[gap]"*. The gap was noticed; the conclusion that essences were nonetheless the roster was not revisited.

**This does not affect Stormseeker.** Lightning is in the essence roster, the rune roster, and the
abilities tree. The questline under construction is untouched by the conflict.

**But it is cheap now and expensive later.** Element identity became a first-class constructor argument
in `f8a26be`, so the roster is baked into `:core`'s API — with exactly one consumer today. N9 §9 already
names this window: *"the last window before questline #2."*

---

## 4. Place nexuses first, then draw lines

The conversation's first instinct was to derive *lines* from linear terrain features (rivers,
ridgelines, snowlines, biome boundaries) and let crossings fall out. The better inversion, proposed by
the operator, is the opposite order: **place the nexuses, then connect them.**

Three reasons it is better.

**It restores control over supply, which is N8's hard constraint.** Deriving everything from whatever
terrain happens to exist cannot promise a usable nexus near spawn, and N8 established that a world with
no usable nexus is *unplayable rather than merely poor*. Placed nexuses are counted and sited
deliberately, so completability is a parameter again rather than a hope.

**It is the engine's own pipeline.** [jar] Worldgen V2's graph system is literally nodes → edges →
density: `SquareGrid2d → Jitter3d → Scaler → Offset` produces positions, node and edge selectors
(`NeighborNodes`, `ConnectionCount`, `LengthEdgeSelector`, `AngleEdgeSelector`) build connections, and a
`DistanceToGraphEdge` density node mapped through a `CurveMapper` produces the field. The topology
question — which nexus connects to which — has shipped machinery behind it.

**It puts the legibility payoff in the right place.** The nexus is the memorable site; lines are
connective tissue. Players remember places, not curves.

### What this changes about mixed nexuses

Under the committed model a mixed nexus **is** a crossing of two currents. Under this one it is a **site
that qualifies for two elements** — a river mouth beside lava. That is a different thing, and it means
crossing detection is no longer what generates nexuses.

**Worth keeping both.** Placed nexuses carry the design's obligations (guaranteed, sited, countable);
emergent line-crossings become *bonus* nexuses, variable per world, the thing exploration rewards. The
crossing mathematics already exists, so this costs nothing to retain.

### How candidate sites are found — the lattice proposes, terrain disposes [proposed]

"Place nexuses at terrain sites whose character matches an element" leaves open *how the sites are
found*. Scanning the world for lava is the obvious reading and it is expensive — §9 question 6.

**A jittered lattice generates the candidates; terrain scores, selects and types them.** Hash a
candidate position per cell from `(worldSeed, cellX, cellZ)` with jitter, then examine a bounded
neighbourhood of each candidate and ask what it is: lava nearby → Fire; river → Water; exposed height
→ Wind; and so on. A candidate matching two elements is a mixed nexus. A candidate matching none
yields nothing.

This is worth stating explicitly because it is the salvaged half of a direction that was otherwise
discarded. Before the terrain-derivation idea, the proposal on the table was a **lattice of
convergences** — tile the single-convergence star so it covers an unbounded world. That was rejected
as the wrong layer: it fixed the mechanism's reach while leaving residue an invisible mathematical
field that would happily site a Fire nexus in a snowfield. But the lattice was never wrong as a
*mechanism*, only as an *answer*, and demoting it from network-generator to **candidate-generator**
keeps what it was good at:

| property | contributed by |
|---|---|
| no region is empty by construction — the original extent problem | the lattice |
| supply density, and therefore N8 completability | the lattice's cell size |
| element coherence — a Fire nexus is where fire things are | the terrain |
| legibility — the site explains itself to a player | the terrain |
| bounded cost — score N candidates, do not scan a world | the lattice |

Note what the lattice does **not** do here: it has no geometric role. Nothing grows from it, arms do
not radiate from it, it is not a convergence. It only proposes locations. That distinction is the
whole difference between this and the rejected version.

**It also gives the cell size a referent**, which the discarded model's parameters never had. Cell
size is "how far should a player walk between nexuses" — a play-feel question with an answer, rather
than a free number fitted to a figure from a deleted harness.

**Three honest consequences.**

- **Candidates can come up empty**, which reintroduces supply variance — the thing placement was meant
  to eliminate. Mitigations are a widening search radius per candidate, or a fallback element for
  otherwise-unqualified sites, or accepting sparser coverage in homogeneous terrain. Undecided.
- **The lattice becomes the mixed-nexus dial too.** How often a candidate's neighbourhood contains two
  elements is a function of cell size against biome scale, so one parameter now controls both total
  supply and mixed-nexus frequency. They may need decoupling.
- **It does not resolve §9 questions 1 and 2.** Scoring still reads terrain, so whether that can
  happen on demand or must happen at generation time is unchanged. What this bounds is the *cost* of
  the read, not its availability.

Determinism survives intact: the lattice is seed-hashed and the terrain is itself seed-determined, so
the whole derivation is reproducible and identical for every player — which R1 requires.

Worth checking before building: V2's own positions pipeline is exactly this shape
(`SquareGrid2d → Jitter3d → Scaler → Offset`), so there may be machinery to reuse rather than
reimplement. [jar]

---

## 5. Per-element derivation [proposed]

Assuming the rune roster of §3:

| element | nexus sites | lines |
|---|---|---|
| **Fire** | lava, volcanic terrain | flows, fissures |
| **Water** | rivers, coasts, lakes — **and ice/snow**, since Water subsumes ice | the rivers themselves |
| **Earth** | stone, caves, mountain interiors | cave systems, strata |
| **Wind** | exposed heights, open sky | ridgelines |
| **Lightning** | storm-frequent environments × exposure | ridgelines, shared with Wind |
| **Void** | invasion portals — see §6 | **none**; Void does not flow |

Two of these are **conditional rather than locational**, and both conditions are already engine data:

- **Lightning** derives from storm frequency. [measured] Session 16 measured storm share across all 134
  shipped environments — Zone 1 at 1.4%, `Env_Zone4_Crucible` at 50%. Tying Stormseeker's element to
  where storms actually occur is better than tying it to a seeded curve, and the data exists.
- **Wind and Lightning both need exposure**, which the engine computes. [jar] `ChunkLightData` carries
  **four** 4-bit channels: `RED_CHANNEL`, `GREEN_CHANNEL`, `BLUE_CHANNEL` and **`SKY_CHANNEL = 3`**. Sky
  light is stored separately from block light, so it measures *how much sky reaches a position* — terrain
  geometry, not torches. That makes it deterministic, identical for every player, and immune to a player
  lighting a cave, which satisfies R1 where a total-light reading would not.

`IHeightmap.getHeight(int x, int z)` supplies terrain height at any column [jar], and `HeightmapColumn`
separates `CLASS_COLLIDES` from `CLASS_OPAQUE` — a walkable top distinct from a light-blocking top.

### The anchor question this also answers

The world-fit work left open what relative `y = 0` means in world terms. Terrain height is the obvious
anchor: a current that tracks the surface is *followable on foot*, which is the operator's own stated
test for whether the height profile is too erratic. A current floating at a constant altitude is not.

**Constraint.** `ResidueNetwork` is pure — *"Nothing is placed, persisted, or read from the world"* — and
`:core:checkNoPlatformImports` enforces it. So terrain data must be **injected** into `core` (the pattern
the shipped weather port already uses) or applied at the consumption layer. It must not be read by
`core` directly.

---

## 6. Void

### What the lore says

Void is **one of the six elemental runes**, **not native to Orbis** — from another world and dimension —
wielded by **Varyn**, the game's principal antagonist, to transform Orbis and its inhabitants into
twisted, corrupted Void creatures. Of the six runes, Void is the only one not connected to Gaia. [lore]

So Void is not an absence and not a darkness. It is a **hostile foreign incursion**. An earlier proposal
in the same conversation — Void as lightlessness, present underground and at night — was dropped for
being lore-incongruent, and is recorded here only so it is not re-proposed.

### Is the Void reachable? No

[jar] The shipped portal destinations are `Portals_Goblins`, `Portals_Goblins_Encounters`,
`Portals_Goblins_Encounters_Cave`, `Portals_Hedera`, `Portals_Henges`, `Portals_Jungles`,
`Portals_Oasis`, `Portals_Taiga`. **There is no Void destination.**

Portals themselves are usable — `PortalDevice` with `PortalDeviceSummonPage` and `PortalSummonPrompt`
(players summon one through a device UI), plus `EnterPortalInteraction`, `ReturnPortalInteraction`,
`LeavePortalCommand` and `LeavePortalButtonUI`. They just do not go to the Void.

**Void portals run the other way.** `VoidInvasionPortalsSpawnSystem` and `VoidSpawner` spawn void
portals *into* Orbis which emit void creatures. The engine models Void exactly as the lore describes: it
comes to you.

### The Void event is already a transient distributed nexus field

[jar] `VoidEventConfig` exposes `getEventStartSeconds()`, `getInvasionPortalSpacing()`,
`getMaxPortalCount(stage)`, `findLatestStage(double)` and `getActiveWeatherId(stage)`; `VoidEventStage`
adds `getSecondsInto()`, `getExtraPortalCount()`, `getForcedWeatherId()`, `getInvasionPortals()`, plus
music, sound and title hooks. Both carry a `CODEC`, so the whole event is asset-authored.

Invasion portals are therefore **spatially spaced, count-bounded, and escalate in stages over time**.
That is already the shape of a transient Void nexus field, readable rather than built.

### The proposal [proposed]

**A Void nexus exists while a Void invasion portal is active, and its presence corrupts nearby Orbis
materials into harvestable void variants.**

This is lore-exact: corrupting native things into twisted forms is precisely what Varyn's Void does.

**And the corrupted materials already ship.** [jar] `Lianas_Void.png` (a tainted plant, under the
foliage moss-wall textures), `Rock_Crystal_Void.png` (a corrupted crystal), `Fluid_Tar_Void.png`,
`Anomaly_Shard_Void.blockymodel`, `Bear_Trap_Voidheart`. Tainted plants and corrupted crystals are not
something this design would invent; the assets exist and this uses them as they appear intended.

**It resolves the R1 tension rather than dodging it.** R1 requires residue to be present from first
login, identical for every player, and gated on no quest state. A Void nexus gated on a **world event**
satisfies that: the event is server-wide and simultaneous. The asymmetry — five permanent, one
ephemeral — stops being a rule violation and becomes what makes Void feel foreign.

**Void has nexuses but no lines**, and that is correct rather than a gap. Lines are how a native element
flows through Orbis. Void does not flow through it; it punctures it.

### Corruption should leave scars

Harvestable corrupted material must be a **real block write**. [measured] Session 17 established that a
per-player spoofed block yields the *real* block's drop, so client-side spoofing cannot produce
corrupted loot.

Real writes imply cleanup, which is the tracking-map / restart / chunk-unload hazard
`2026-10-20-hytale-0.7.2-capability-assessment-design.md` §4 rejected for block swap. **The proposal is
therefore not to clean up.** A Void incursion permanently marks the land it touched. That is
thematically stronger than tidy reversion, removes the cleanup problem entirely, and leaves a world
history players can read.

If reversion is wanted later, `ReplaceBlockTypeEffect` with `DeleteVolumeEffect` is a shipped
trigger-volume mechanism to evaluate before hand-rolling one. [jar]

---

## 7. What worldgen V2 supplies, and the trap it sets

[jar] V2 is large and asset-authored: **1106 classes** and **351 shipped JSON assets** under
`Server/HytaleGenerator/`. `HytaleGenerator.get()` exposes `getAssetManager()` →
`getWorldStructureAsset(String)`, `createStagedChunkGenerator(profile, worldStructure, settings)` and
`submitChunkRequest(...)`. `IWorldGenProvider` is implementable with `CubicTestWorldGenProvider` as a
worked example.

`GraphSpace` is nearly the store interface this design needs:

```java
void getNodes(Vector3dc centre, double radius, List<Node>)
void getNodes(Bounds3d, List<Node>)
Collection<Edge> getEdges()
void trim(Bounds3d)
```

`trim` is the tell: a trimmable graph is a **window** over unbounded space, which is what an infinite
world forces.

**But `GraphSpace` is generation-scoped.** A plain class, not a `Resource`, nothing persists it, no
`getGraphSpace(world)` exists. It shapes blocks while a chunk generates and does not survive as a
queryable field. Residue needs density at arbitrary positions at **runtime**, so a pure or persisted
implementation still has to exist regardless.

**The trap.** Deriving terrain from JSON *and* the runtime field from `core` is two implementations of
one structure — the exact shape N9 §3 rejected when it refused to let a nexus be *"two objects that must
agree"*. Any adoption needs **one source of truth** for placement, not a JSON copy of it.

**The likely resolution** [proposed]: discover nexus sites at **generation time**, write them to
`PointManager` — which is persisted per world, KD-tree indexed, radius-queryable and survives restart
[measured, §5.2 probe] — and query that at runtime. One derivation, one store, no drift. Note
`PointManager`'s measured constraint: tag-filtered radius queries match on the tag **key**, not
`key:value`, so element typing must live in the key.

---

## 8. What survives and what retires

Retiring the seeded walk does **not** discard most of the committed work.

| Component | Fate |
|---|---|
| `WorldPoint2d`, `FlowVector`, `ResidueRandom`, `SegmentMath` | **survive** — primitives are independent of how lines are produced |
| density falloff, influence radius, `nexusRadius` | **survive** — distance-to-line is still the field |
| `|Δy|` 3D eligibility (N9 §4) | **survives** — still needed for placed sites and crossings |
| `CurrentGeometry.crossings()` | **demoted** — no longer generates nexuses; retained for bonus crossings (§4) |
| `GrandConvergence` | **retires** — one convergence per world is the defect |
| the seeded arm walk | **retires** — replaced by terrain-derived placement |
| absorption radius (N9 §7) | **re-derive** — it existed to thin a star's hub; placement has no hub |
| height calibration `restore`/`maxPitch` | **re-derive** against terrain anchoring (§5) |

Roughly five or six of the thirteen committed residue commits are affected. Nothing in `:core`'s pure
mathematics is wasted.

---

## 9. Open questions, ranked by what they block

1. **Is sky exposure cheaply queryable at an arbitrary position**, or only for loaded chunks? Decides
   whether derivation can be on-demand or must be generation-time. Blocks §5 and §7. Unverified.
2. **Is biome/environment queryable positionally**, rather than only per tracked entity? Same
   consequence. `WorldMapTracker.getCurrentBiomeName()` and `getWeatherIndexForEnvironment(int)` are both
   entity-anchored. Unverified.
3. **Does a Void event fire on its own?** `getEventStartSeconds()` exists, as do `VoidEventCommands` and
   `StartVoidEventCommand`, and `StartVoidEventInFragmentSystem` hints at automatic start in some
   "Fragment" context. If it only fires by command, we must schedule events ourselves — possible, but it
   changes the character from *the world does this* to *we stage it*. Blocks §6.
4. **How long does a Void event last?** Readable from config rather than guessed. Blocks §6's harvest
   loop being a window rather than a chore.
5. **Roster decision.** Rune roster or essence roster. Cheap until questline #2, then not. Blocks §3,
   §5, and `:core`'s element identity.
6. **Does placement need a terrain scan, and at what cost?** **Partly answered — see §4's
   "lattice proposes, terrain disposes".** A jittered lattice bounds the work to scoring N candidate
   neighbourhoods rather than scanning a world. What remains open is the empty-candidate case, and
   whether cell size can control supply and mixed-nexus frequency independently.
7. **Supply targets under placement.** N1's `6×240` and N8's `nullRadius` were fitted to the walk model
   and do not transfer.

---

## 10. What this does not claim

- Nothing here is probed. §5 and §6 are proposals resting on jar reads and one conversation.
- The engine using a jittered lattice for its own generation is evidence about **what its authors found
  workable**, not evidence about this design.
- The lore tier is the weakest (see §12), and the roster question should not be settled on wiki
  authority alone — the asset corroboration in §3 is the part that stands without it.

---

## 11. Relationship to committed documents

| Document | If this direction is adopted |
|---|---|
| `2026-08-27-residue-network-design.md` | §2's generation model superseded; the convergence-star premise retires |
| `2026-08-29-six-element-residue-framework-design.md` | roster superseded by §3; the essence derivation becomes a rejected reading |
| `2026-08-30-n9-all-crossings-are-nexuses-design.md` | §1's crossing-as-nexus definition demoted (§4); §8's supply arithmetic and §10's wild-availability problem both moot, since extent is no longer bounded |
| `2026-08-28-graded-nexuses-design.md` | §3's height calibration re-derived against terrain anchoring |
| `2026-10-20-hytale-0.7.2-capability-assessment-design.md` | §5.8's abilities row needs the §3 correction — its roster reading was wrong |
| `scripts/residue-scans/` | measures the retiring model; keep as the record of why it retires |

**None of those have been retired.** Until a decision is taken, they remain the committed design and
this document remains a proposal.

---

## 12. Sources, and how much to trust them

Asset and jar claims are first-party and re-derivable with `javap` and `unzip` against the installed
build — the strongest tier here, and the tier the §3 roster correction rests on independently of lore.

Lore claims come from **community wikis**, not first-party documentation, and Hytale's lore has been
revised repeatedly across development:

- [Void — Hytale Wiki](https://hytale.fandom.com/wiki/Void)
- [Varyn — Hytale Wiki](https://hytale.fandom.com/wiki/Varyn)
- [Elements — Hytale Wiki](https://hytale.fandom.com/wiki/Elements)
- [Element — hytalewiki.org](https://hytalewiki.org/w/Element)
- [Water Element — Hytale Wiki](https://hytale.fandom.com/wiki/Water_Element)

Treat the rune roster as strong but not final. What makes it more than wiki-deep is the convergence:
the lore roster and the shipped `Server/Abilities/Elements/` tree name the same five Gaia elements, and
that agreement is verifiable without trusting any wiki.
