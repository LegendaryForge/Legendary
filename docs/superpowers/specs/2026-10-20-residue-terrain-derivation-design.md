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
no execution; **[lore-1p]** means a first-party lore artifact — Hypixel's own concept art, site or
in-game text; **[lore]** means community-wiki sourced, which is the weakest tier here — see §12;
**[proposed]** means it came out of the conversation and rests on nothing yet.

**Lore outranks the jar on questions of meaning** (see `CLAUDE.md` § "Lore first"). The jar is
authoritative about what the engine *does* and carries no authority about what anything *is*. §3 is the
worked example: five asset files corroborated a roster and the roster was still wrong.

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

## 3. The roster is wrong in its membership, not in its vocabulary

**Revised 2026-10-21.** The first version of this section reached the right destination by the wrong
road. It argued the roster against **runes**, and so implied `:core` should stop speaking of essences.
That is backwards: *essence* is the lore's own word for the layer residue models, and the word should
stay. Only the membership changes. The original reasoning is preserved at the end of this section
because the mistake is instructive.

### Three layers, not two

Lore distinguishes them, and the project had collapsed them:

| Layer | What it is | Source |
|---|---|---|
| **Essence** | the world-substance — *"five magic essences that **compose life on Orbis**"* | *"Gaia and the Elemental Essences"*, concept art on Hytale.com, by technical artist Thomas Frick [lore-1p] |
| **Element** | the natural force; *"come in six forms"* | Element, Hytale Documentation Wiki [lore] |
| **Rune** | the **written character** that represents an element | *"These natural forces are **represented by** runes"* [lore] |

A rune is a sigil, not a substance. The Rune page defines one as *"a General Term that describes any
ancient character which have a purpose or meaning"* — and elements are only its *"first known example"*:
Trorks, Fauns and Slothians have faction runes, and Gaia, Varyn and the Sand Empress each have their
own. [lore] So "rune" is a notation system with elemental members, not an elemental taxonomy. Asking it
for a roster is a category error.

The shipped build agrees, and says so twice. `Server/Abilities/Elements/*.json` are five one-line files,
each `{"DamageCause": "X"}` — elements are a **damage taxonomy**. [jar] And the shipped runes are an
ability-socket system — `Rune_Fireball`, `Rune_ChainHook`, `Rune_Aoe_Up` ("Colossal"), `Rune_Thrift` —
crafted at a goblin tinkerer's bench, whose only elemental contact is `Rune_Convert_Wind`: *"Converts a
supported ability's damage type to wind."* [jar] A rune **points at** an element. Nothing in the engine
treats a rune as world-substance, so nothing in the engine supports keying a residue field to one.

Essence, by contrast, is already residue in the game's own words:

> **Lightning** — *"the true source of such magic is nowhere to be found, the elementals born from its
> **lingering traces** still wander the lands."*
> **Fire** — *"This energy once gave form to the dreams of Crucible's artisans. Today it merely
> **lingers** in the creatures that dwell amid the desolated volcanic regions."* [jar]

Those are `server.lang` strings, not wiki prose. The vocabulary question is therefore settled in favour
of essence on first-party evidence alone.

### There are two essence rosters, and the first version merged them

|  | Fire | Water | Lightning | Wind | Earth | Ice | Life | Void |
|---|---|---|---|---|---|---|---|---|
| **Cosmological** — Gaia's five pearls | ✓ | ✓ | ✓ | ✓ | ✓ | | | |
| **Shipped essence items** — 0.7.2 [jar] | ✓ | ✓ | ✓ | | | ✓ | ✓ | ✓ |
| **`:core` today** | ✓ | ✓ | ✓ | | | ✓ | ✓ | ✓ |

They overlap on **three**. And these are only two of **four** overlapping elemental taxonomies the game
ships — the ability `DamageCause` list (`Earth, Fire, Lightning, Water, Wind`, exhaustive across all
shipped JSON) and the Elemental Circle monuments (`Earth, Fire, Frost, Poison, Sand`) are the other two,
and no two of the four are the same list. All four are tabulated with what each is keyed to in
`../../setting/hytale-orbis-setting-brief.md` §3 *"Four rosters, no two identical"*. **That is why this
question was got wrong twice: the jar does not answer "which elements does Hytale have", only "keyed to
what".**

So `:core` is not wrong by accident — it matches the shipped item roster
exactly. It diverges from the **cosmological** one, and the earlier "four right" figure came from
comparing against the six *elements* (Void included) rather than the five *essences* (Void excluded,
because Void is neither Gaia's nor native to Orbis).

### The derivation mechanism picks the roster

This document proposes deriving residue from **terrain**, and that choice decides the question. Lore
puts elements in the ground: *"some zones of Orbis appear to follow a possible elemental theme, with
earth, fire, water, and lightning depicted"*, and *"each of the elements appear to be strongly connected
to specific Alterverses."* [lore]

The fit is mechanical, not merely thematic:

- **Wind and Earth are the two elements terrain reads best** — sky exposure and stone are the cheapest
  signals in §5, and they are exactly the two the shipped item roster lacks.
- **Ice and Life are the two it reads worst.** Water's own lore *"represents the magic of water and
  ice"*, so Ice is already subsumed rather than standing alone. Life is *"found in abundance around plant
  life on Orbis"* — present almost everywhere vegetation is, which makes it a constant rather than a
  field, and a field is the whole mechanism.
- **Void is not a roster member at all.** It is a staged incursion (§6), and its own item text makes it
  a property of creatures rather than of ground: *"unclear whether creatures of the Void produce this
  essence, or if its presence is required to sustain their existence."* [jar]

### Recommendation

**Roster: Fire, Water, Lightning, Wind, Earth — plus Void handled separately as an incursion.** This is
the "five-plus-one" the title already names; what changes is that it now rests on the essence layer
rather than the rune layer. The API keeps the word **essence**. Drop Ice and Life; add Wind and Earth.

> **✅ DECIDED 2026-10-21 — this recommendation was adopted.** See
> [`2026-10-21-residue-roster-decision.md`](2026-10-21-residue-roster-decision.md), which is the
> authority on it. Recorded there as a **reversible modelling default** rather than a taxonomy claim,
> with an anchor and a falsifier for each piece we invented, and with the opaque `elementId` named as
> the mechanism that keeps it reversible. One argument from this section's neighbourhood was
> **withdrawn** there: weighing rosters by shipped creature-drop counts measures build order, not
> taxonomy. §9 question 5 is now closed. The rest of this document remains PROPOSED.

**Nothing was implemented, because there is nothing to implement.** Changing `:core`'s element
identity turned out to be a no-op: `elementId` is opaque and has zero production consumers.

**This does not affect Stormseeker.** Lightning is in the cosmological roster, the shipped roster, and
the abilities tree. The questline under construction is untouched.

**It is cheap now, and it is cheap later too — this paragraph was wrong.** [measured 2026-10-21]

It read: *"Element identity became a first-class constructor argument in `f8a26be`, so the roster is
baked into `:core`'s API — with exactly one consumer today. N9 §9 already names this window: the last
window before questline #2."* The first clause is true and **the inference from it is backwards**:
making identity a *parameter* is the opposite of baking a roster in — it makes the roster the caller's
business.

Read off the code rather than recalled:

- `elementId` is an opaque `ResourceId`. **No enum, no list, no validation, no default.** There is
  nothing in `:core` that knows how many elements exist or what they are named.
- **Every** `DefaultResidueNetwork(seed, elementId, params)` construction in the repository is **in a
  test**, and the only element names in any source file are `ResourceId.of("test", "lightning"|"fire"|
  "water")` inside `ElementIdentityTest`.
- Production consumers: **zero**, not one. `NullCoreResidueNetwork` in `quests/stormseeker` is the only
  production residue class outside `:core`, and it is a decorator over an injected `ResidueNetwork`
  delegate — it never constructs one and never supplies an id.

So the roster carries **no code cost and no deadline.** Nothing breaks whenever it is decided, before
or after questline #2. That removes the urgency this section claimed, and it does not weaken the
recommendation — it just means the recommendation can be adopted as documentation and content without
an API change.

**Why it went unnoticed:** `CLAUDE.md` already states the heuristic that would have caught it — *"before
building on any extension point here, grep its call sites. If the only callers are its own tests, it is
a design, not a mechanism."* It is written there about the questline framework. The residue network has
the same shape, and nobody applied it.

### What the first version got wrong, and why it is worth recording

It asserted *"Hytale's six elemental runes are Wind, Earth, Lightning, Water, Fire, Void"* and concluded
*"our roster is four right, with Ice and Life that are not runes and Wind and Earth missing."* Both
halves mislead: runes are not a roster, and the comparison set was the elements rather than the essences.

The cause is traceable. `hytalewiki.org`'s Element page — fetched and verified — carries the
rune-representation and zone-element claims **and omits the essence framing entirely**. A reader working
from that page alone sees runes and no essences, which is precisely the conclusion that was drawn. The
essence framing lives on the Hytale Documentation Wiki and on the concept art it cites.

It also overturned a correct reading. `2026-10-20-hytale-0.7.2-capability-assessment-design.md` §5.8 had
examined the same five files hours earlier and dismissed them as *"a damage-type list for combat"* whose
overlap with our essences was *"coincidental naming"*. The first §3 accepted the content reading and
rejected the roster reading — *"that is the rune list"*. **§5.8 was right on both counts and is hereby
reinstated**; the entry for it in §11 is withdrawn.

The generalisable form: **the first version corroborated its roster against the assets and still got the
roster wrong**, because asset corroboration can only confirm that a list exists — it cannot tell you
that the list answers a different question than the one you are asking. `Server/Abilities/Elements/` does
name five things that match the lore's five. They are damage types. And the cost of that error was not
only a wrong roster: it was confident enough to overturn a colleague document that had the answer.

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

Assuming the cosmological essence roster recommended in §3 — Fire, Water, Lightning, Wind, Earth,
with Void separate (§6):

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

### Terrain is the first arrow of a two-arrow chain

> **⚠️ DECIDED 2026-10-21 — this subsection is no longer a proposal.** The upstream framing below was
> adopted; see [`2026-10-21-residue-two-arrow-chain-decision.md`](2026-10-21-residue-two-arrow-chain-decision.md),
> which is the authority on it. That document also records the outlier tension it accepts, and
> **defers** the question of whether derivation keys on environment ids or terrain features — a
> mechanism question this decision deliberately does not settle. The rest of §5, and the rest of this
> document, remain PROPOSED.


**The game does not put essence in the ground. It puts essence in living things.** Every shipped
description locates it that way: Fire *"lingers in the creatures that dwell amid the desolated volcanic
regions"*, Lightning's *"elementals born from its lingering traces still wander the lands"*, Water's
*"as water filters through their gills, the concentration grows"*, Life *"found in abundance around
plant life on Orbis"*. [jar]

That is not only flavour text — it is the economy. Essences are obtained from drop tables, and the
tables are creature-typed and the creatures are biome-typed: [jar]

| essence | NPC drop tables | what they are |
|---|---|---|
| Fire | **20** | Emberwulf, Firesteel golem, Burnt + Incandescent skeletons, Magma slug/snail, Ember spirit |
| Ice | **12** | Frost skeletons, Yeti, Frost snail/spirit/zombie |
| Void | **12** | Void crawler/eye/spawn/spectre/tentacle, Outlander cultists, Voidtaken goblin |
| Life | **1** + 64 crop tables | Root spirit; otherwise farmed |
| Lightning | **1** | `Drop_Spirit_Thunder` |
| Water | **0** | none |

So the world's actual chain is **terrain → creatures → essence**, and §5 as written reads the first
arrow and skips the second. Two consequences, one reassuring and one not.

**The correlation is real**, which is why the shortcut works at all. Fire's twenty sources are volcanic
creatures and Ice's twelve are frozen ones; deriving Fire from lava lands in the same places the game
already puts fire-essence mobs. Terrain is a sound *proxy* for the intermediary here.

**But the chain is not uniform, and it is thinnest exactly where we need it.** Lightning has **one** NPC
source in the entire game and Water has **none** — and Lightning is Stormseeker's element. Terrain
derivation will therefore site Lightning and Water residue across large regions in which the shipped
game places no essence creatures at all. Our field would be making a claim about the world that the
world's own content does not corroborate, for two of the five roster members.

**Resolution: put residue upstream of the creatures rather than beside them.** [decided 2026-10-21]
Residue is the **field**; essence items are what the field's creatures yield. Terrain shapes residue,
residue explains why those creatures live there, creatures drop the items the player collects:

```
terrain  →  residue field (ours)  →  creatures (the game's)  →  essence items (the game's)
```

Read that way the mod is not bypassing the chain — it is supplying the missing causal first term for
data the game already ships, and the Fire and Ice drop tables become corroboration rather than
coincidence. It also absorbs the uneven-supply problem instead of tripping on it: thin Lightning and
Water sourcing becomes *content not yet built on top of a field that is already there*, which is a
coherent thing for a mod to say and a natural hook for questline content.

**This is a fiction decision, not a mechanism change** — nothing in §5's derivation moves. What it fixes
is what the derivation *means*, and it needs settling before the roster is implemented rather than
discovered later as a lore inconsistency. Per `CLAUDE.md` § "Lore first", where lore speaks we follow;
here it speaks about the source of essence, and §5 had quietly answered differently.

### The chain is engine data, and the spawner reads the same signals we proposed [jar]

**Added 2026-10-21.** The two-arrow chain above was argued from item descriptions. It is also in the
spawn tables, which makes it mechanism rather than theme.

**Creatures are bound to places by `Environments` + `SpawnBlockSet`:**

```json
Server/NPC/Spawn/World/Zone4/Spawns_Zone4_Volcanoes_Predator.json
{ "Environments": ["Env_Zone4_Volcanoes"],
  "NPCs": [ {"Id":"Emberwulf"}, {"Id":"Skeleton_Burnt_Gunner"},
            {"Id":"Zombie_Burnt"}, {"Id":"Spark_Living"}, {"Id":"Toad_Rhino_Magma"} ],
  "DayTimeRange": [6, 18] }
```

Every NPC there is a Fire-essence dropper. Joining 290 NPC drop tables to 523 spawn tables gives the
**essence → environment** map:

| Essence | droppers | world-placed | environments | character |
|---|---|---|---|---|
| **Fire** | 20 | 13 | 14 | `Caves_Volcanic_T1..T3` (Zones 1–2), `Zone4_Volcanoes`, `Zone4_Wastes`, `Zone4_Caves_Volcanic` — **plus leakage** into `Zone1_Azure`, `Zone1_Swamps`, `Zone4_Forests`, `Zone4_Sewers` |
| **Ice** | 12 | 6 | 6 | `Zone3_Glacial`, `Glacial_Henges`, `Mountains`, `Forests` — **and `Zone3_Caves_Volcanic_T2/T3`**, frost creatures in volcanic caves |
| **Void** | 12 | 3 | 16 | spread across Zones 1–3 — `Plains`, `Deserts`, `Savanna`, `Tundra`, `Glacial`, … **uniform, not terrain-bound** |
| **Lightning** | 1 | **0** | 0 | `Spirit_Thunder` is in **no world spawn table at all** |
| **Water** | 0 | 0 | 0 | none |
| **Life** | 1 | 0 | 0 | `Spirit_Root`; otherwise 64 crop tables |

Three things follow, and one of them is sharper than the version above.

**The correlation is real but not clean.** Fire's environments are dominated by volcanic ones, Ice's by
glacial — so terrain is a sound proxy. But Fire leaks into swamps, forests and sewers, and Ice appears
in volcanic caves. A strict terrain→element derivation will **disagree with the shipped game in
specific places**, and that is a thing to accept deliberately rather than discover in play.

**Lightning is worse off than the drop-table count suggested, and Void better explained.** Lightning's
sole source is not merely rare — it has **no ambient world placement whatsoever**, so it is
encounter/beacon-spawned only. Terrain-derived Lightning residue therefore has *zero* creature
corroboration anywhere. Conversely Void's droppers span 16 environments across three zones with no
terrain character at all, which **independently confirms §6's reading** that Void is an incursion rather
than a feature of ground — a decision that was taken on lore and is now corroborated by placement data.

**And the decisive one: the spawner reads the same signals §5 proposed.** `NPCSpawn`'s public API is
`getEnvironments()` / `getEnvironmentIds()`, `getLightRange(LightType)`, `getDayTimeRange()`,
`getMoonPhaseRange()`, and `RoleSpawnParameters.getSpawnBlockSet()`. `LightType` is
`{Light, SkyLight, Sunlight, RedLight, GreenLight, BlueLight}`. So the engine's own creature-placement
predicate is

```
environment  ×  sky-light range  ×  block set  ×  time of day / moon phase
```

which is the same three *spatial* inputs §5 derives residue from (environment, sky exposure, terrain
blocks). **Residue built on those signals agrees with creature placement by construction, because it
reads what the spawner reads.** That moves the proposed fiction — *residue explains why these creatures
are here* — from lore-compatible to near-isomorphic with the engine's own rule. It is the strongest
argument yet for the upstream framing, and it was not available when that framing was proposed.

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

1. **ANSWERED 2026-10-21 — sky exposure is positionally readable, for loaded chunks only.** [jar]
   `ChunkLightData.getSkyLight(int, int, int)` is **public**, alongside the `SKY_CHANNEL` of four 4-bit
   channels. It is chunk-*section* data, so it requires the chunk loaded and lit; there is no
   arbitrary-position form. (The getter was nearly recorded here as absent: a `javap | grep | head -12`
   truncated it out of view. An absence produced by a pipe — the same family as the gate-verdict rule in
   workspace `CLAUDE.md`.)
2. **ANSWERED 2026-10-21 — environment is positionally readable for loaded chunks; the
   arbitrary-position API exists but belongs to a generator this world does not run.** [jar]
   `EnvironmentChunk` is a `Component<ChunkStore>` with `sliceToSection(int)`, and
   `EnvironmentColumn.get(y)` returns an environment id — so it is chunk-attached state, like light, not
   entity-anchored as this question assumed. Separately
   `EnvironmentProvider.evaluate(Context) -> int` **is a pure function of position** — its `Context`
   holds nothing but an `org.joml.Vector3i` — which would allow querying any coordinate without loading
   a chunk. But it lives in `builtin/hytalegenerator` (998 classes), and
   `HytaleWorldGenProvider` — the generator the live world actually uses — references that package
   **zero** times.

   > **So the fork resolves, for now: on-demand derivation is possible but bounded to loaded chunks**
   > (in practice, near players). Anything world-wide must be generation-time. Both signals land the
   > same way because both are chunk state, which is a more coherent answer than the question expected.
   >
   > **This couples to `[[Workspace_Deferrals]]` #18.** When the `HytaleGenerator` tree takes over
   > generation, `EnvironmentProvider.evaluate` becomes reachable and arbitrary-position environment
   > queries become available — reopening this fork in the *other* direction. #18's handover trigger
   > was filed as an asset-contribution question; it now also gates an architectural one.
   >
   > **Engine facts from this investigation now live in
   > `../../integration/hytale-engine-api.md`**, with build stamps and evidence tiers, rather than
   > only here. A design gets superseded; an API fact outlives the design that found it.
   >
   > **Partly probed 2026-10-21, and the probe narrowed the answer.** `/signalprobe` on a headless
   > `0.7.2` server returned `INCONCLUSIVE` for q1 and q2 — **because a headless server loads no
   > chunk region at all** (nothing in memory at any offset 0..4096). That is itself a result: the
   > bound on "works in loaded chunks" is *empty* with no player connected, so derivation cannot
   > pre-compute a region before a player arrives. What the same run **did** settle: the world is
   > 320 blocks tall starting at y=0 (`canHoldY` y319 true / y320 false / y-1 false — previously
   > inferred, now measured), and `supportsCubicSections()` is `false`, so the V1 shape is live.
   >
   > **Still outstanding, and it needs a connected client:** whether `getSkyLight` takes world or
   > section-local coordinates, which of global/local light carries sky, and what the reads cost.
   >
   > **Signature-level remainder.** Per the standing method rule, a signature is a hypothesis:
   > unverified here are whether a mod can reach the world's `EnvironmentChunk` component and
   > `ChunkLightData` at runtime, and what `getSkyLight` and an environment read actually *cost* — which
   > is what "cheaply" in this question's original wording was asking. One live probe settles both.
3. **ANSWERED 2026-10-21 — it fires on its own, but it is Fragment-scoped, not world-scoped.** [asset]
   The config hangs off a *gameplay config* under `Plugin.Portal.VoidEvent.Stages`, and only two of the
   eight shipped gameplay configs carry one — `Portal.json` and `PortalsGoblins.json`, both portal
   destinations. `StartVoidEventInFragmentSystem` is a `DelayedSystem` whose only method is
   `delayedTick`. So the answer is neither of the two this question offered: the world *does* do it, and
   the world that does it is a **Fragment you travel to**, not the player's own. Commands exist too, so
   it is also manually triggerable. **Full detail, tiers and the confirming probe:
   `../../integration/hytale-engine-api.md` § "The Void event".** What §6 still needs is whether a *mod*
   can attach a `VoidEvent` to a world it defines — unverified.
4. **ANSWERED 2026-10-21 — it has no duration; it escalates and then evicts you.** [asset]
   `PortalsGoblins.json` stages at **120 / 240 / 390 / 540 / 690 / 780 seconds** (Faint → Mild → Light →
   Heavy_Surge → Heavy_Late → Storm); `Portal.json` at **420 / 540**, spawning `VoidInvasion_Portal`
   blocks at the first. There is no terminal stage and no duration field — the Fragment ejects you:
   *"The Veil of Void has closed once more / Your presence is no longer permitted"*, with `VOID_EVICTION`
   as a first-class title style. **So §6's harvest loop is a window, and the window is the visit** —
   roughly two to thirteen minutes of rising pressure bounded by being thrown out rather than by a timer
   expiring, which is a stronger pacing device than this question assumed.
5. **Roster decision. ✅ CLOSED 2026-10-21** — adopted as a reversible modelling default; see
   [`2026-10-21-residue-roster-decision.md`](2026-10-21-residue-roster-decision.md). History below.
   **Narrowed earlier the same day, then closed.** No longer "rune roster or essence
   roster" — that was a false pair (§3: runes are notation, not a taxonomy). The vocabulary is settled as
   *essence*; what remains is **which essence roster** — Gaia's cosmological five, or the six shipped as
   items. §3 recommends the former and gives the mechanical reason. **There is no `:core` change to
   authorise** — verified 2026-10-21: the roster is not in the code at all, `elementId` is an opaque
   `ResourceId`, and every construction is in a test. So this is a documentation and content decision
   with no code cost and **no deadline**; the "cheap until questline #2, then not" framing was wrong.
   **Precondition DISCHARGED 2026-10-21** — §5's
   *"Terrain is the first arrow of a two-arrow chain"* is decided (upstream framing adopted; see
   [`2026-10-21-residue-two-arrow-chain-decision.md`](2026-10-21-residue-two-arrow-chain-decision.md)).
   It had been added because the game sources essence from creatures, not ground, and two roster
   members (Lightning, Water) have almost no creature sourcing at all — so what the derived field
   *means* had to be fixed before the roster it is keyed to. That is now settled, which leaves the
   roster decision unblocked and unchanged in character: documentation and content, no code cost, no
   deadline.
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
- The community-wiki tier is the weakest (see §12). But the roster question is **not** settled on wiki
  authority: the essence framing traces to a first-party concept-art title, and the residue-shaped
  essence text is in `server.lang`. Note the inversion §3 earned — the asset corroboration was the part
  believed to stand without lore, and it is the part that misled; the assets confirmed a list without
  revealing it answered a different question.

---

## 11. Relationship to committed documents

| Document | If this direction is adopted |
|---|---|
| `2026-08-27-residue-network-design.md` | §2's generation model superseded; the convergence-star premise retires |
| `2026-08-29-six-element-residue-framework-design.md` | roster **membership** superseded by §3; its essence *vocabulary* is vindicated, not rejected — Ice and Life retire, Wind and Earth arrive |
| `2026-08-30-n9-all-crossings-are-nexuses-design.md` | §1's crossing-as-nexus definition demoted (§4); §8's supply arithmetic and §10's wild-availability problem both moot, since extent is no longer bounded |
| `2026-08-28-graded-nexuses-design.md` | §3's height calibration re-derived against terrain anchoring |
| `2026-10-20-hytale-0.7.2-capability-assessment-design.md` | **no change needed — §5.8 was right.** The first §3 "corrected" it; the revised §3 withdraws that. See below |
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

**Revised 2026-10-21.** The previous verdict — *"treat the rune roster as strong … the lore roster and
the shipped `Server/Abilities/Elements/` tree name the same five Gaia elements"* — read a real agreement
and drew the wrong conclusion from it. The five asset files are damage types (§3). Convergence between a
roster and an asset list is not evidence that the asset list is a roster.

Two source notes worth keeping for the next reader:

- **`hytalewiki.org`'s Element page omits the essence framing**, while carrying the rune-representation
  and zone-element claims. Verified by fetch. It is the likeliest origin of the original error.
- **Fandom returns HTTP 402 to automated fetching.** The Rune definition and the concept-art title are
  therefore quoted from search snippets, corroborated across four independent sites
  (`hytale-game.fandom`, `hytalewiki.org`, `hytalespy.com`, `g-portal`) plus the gamepedia mirror. Treat
  them as well-attested but not fetch-verified; re-check by hand before anything irreversible rests on
  the exact wording.

Added sources:

- [Element — Hytale Documentation Wiki](https://hytale-game.fandom.com/wiki/Element) — the essence framing
- [Rune — Hytale Wiki](https://hytale.fandom.com/wiki/Rune) — the general definition of a rune
- [Gaia — Hytale Wiki](https://hytale.fandom.com/wiki/Gaia) — the five ponytails and their elements
- *"Gaia and the Elemental Essences"* — concept art, Hytale.com (cited by the above; **first-party**)
