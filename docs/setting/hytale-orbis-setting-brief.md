# Orbis — A Setting Brief for Legendary Content

> **Purpose:** give anyone designing a Legendary questline enough of Hytale's world to write
> *inside* it rather than beside it. This is a durable document: it describes a fictional setting,
> not an API, so it decays slowly. Engine facts live in
> `../integration/hytale-capability-audit.md`, which decays fast — do not merge the two.
>
> **Sources.** Every claim is tagged:
> **[asset]** — read out of the shipped game (`Assets.zip` 0.5.9, `server.lang`, asset JSON). Authoritative.
> **[official]** — Hypixel Studios blog or verified studio statement.
> **[gap]** — something the game does *not* contain, verified by exhaustive search. These are
> opportunities, and they are marked because a gap is a claim like any other.
>
> Compiled 2026-08-24 against game build **0.5.9**. **Partially re-verified 2026-10-21 against
> 0.7.2** — §3's magic hierarchy and vocabulary check were re-measured and corrected, and §6's
> Elemental Circle gap was re-confirmed. **Everything else in this document is still 0.5.9-verified
> and has not been audited against 0.7.2.**
>
> **An `[asset]` claim carries a build, and this document did not say so.** It opens by calling itself
> "durable … it describes a fictional setting, not an API, so it decays slowly", and separates itself
> from the fast-decaying capability audit. That split is right about the *fiction* and wrong about the
> *verification*: the `[asset]` tier decays at the game's pace, and the game auto-updates without
> notice. The cost was concrete — §3 asserted "There are no *runes*", true of 0.5.9 and false of 0.7.2,
> which shipped an entire runestone system. A later design argued a roster against runes partly because
> this document said runes did not exist. Read any untagged-as-re-verified `[asset]` or `[gap]` claim
> below as *"true of 0.5.9"*, and re-measure before building on it.

---

## 1. The frame

**Orbis is the world.** [asset] Named constantly in item flavour text: copper is "found throughout
all regions of Orbis"; linen is used "by many of Orbis' species"; the Memories bench is literally
named **"The Heart of Orbis"**.

**The player is an Avatar.** [asset] Not a metaphor — the game's own text says so: a bed "allows an
Avatar to set their respawn point"; the architect's bench notes "Even an Avatar needs a few
comforts"; the workbench observes that "Even Avatars need time to hone their craft, **particularly
when bound by the rules of another's domain**."

**Gaia is the Avatar of Orbis.** [official] Confirmed in the studio's January 2026 lore post as
"still very much a part of our canon". [asset] Corroborated in shipped content: two Gaia statues
exist as furniture — `Furniture_Temple_Dark_Statue_Gaia` = **"Outlander Deity Statue"** and
`Furniture_Temple_Wind_Statue_Gaia` = **"Statue of a Silent Deity"** — plus broken Gaia statues in
Human Ruins decorative sets and an `Env_Temple_of_Gaia` environment. **Not every faction knows
Gaia is the Avatar** [official]; the two statue names show the same figure worshipped under
different understandings.

**Varyn, Tessa and Kyros** are returning canon characters, and **Alterverses** are reserved for
long-term overarching narrative. [official] None of the four appears anywhere in shipped 0.5.9
content. [gap]

**The Cursebreaker arc** is the main narrative thread of Exploration Mode, delivered as chapters;
when it reaches its final chapter, "Orbis will be within reach and the game will be fully
released". [official] In 0.5.9 the curse is described as *quiet*. Shipped Cursebreaker content is
cosmetic only — a gold cape, a portal logo, an experimental keyart biome. [asset] **The story
space is announced and empty.** [gap]

---

## 2. How this world tells stories — and why it matters to us

The studio calls its method **"archaeology"**: [official]

> *"Much of our lore … will be uncovered by exploring the world and uncovering hints left in the
> form of structures."*

Exploration Mode is explicitly **not** "a linear campaign with constant quest text and cutscenes".
You read the world by exploring it, and the same site can be interpreted differently by different
factions.

**This matters more than any other line in this document.** The Stormseeker canonical doc
independently arrived at the same principle — *"No quest UI required. The world teaches the player
through feel, not markers."* Our narrative philosophy and Hytale's are the same philosophy. That
is the strongest argument for building canon-native rather than canon-adjacent: we would not be
bending our design to fit the setting, we would be removing the seam between two designs that
already agree.

The shipped item flavour text is the model to imitate. It is short, italicised, in-world, and
almost always tells you something oblique about a fallen civilisation:

> *"Orbis' most advanced civilizations shared a common trait — the hubris that ultimately led to
> their downfall."* — `Ingredient_Bone_Fragment`
>
> *"For the last peoples of Orbis, fire was both a source of creativity that elevated their
> civilization to new heights, and the force that would ultimately lead to their undoing."*
> — `Furniture_Crude_Torch`

**Recurring theme:** advanced civilisations, hubris, self-caused downfall, ruins left behind.

---

## 3. Magic

There is a clear, shallow hierarchy. [asset]

**Arcane arts** are mortal magic. From `Bench_Arcane`, "**Arcanist's Workbench**":

> *"The arcane arts are the closest mere mortals have ever come to achieving Avatar powers. Had
> they mastered them sooner, perhaps they would not have been so easily swayed."*

Two things are packed in there: arcane sits *below* Avatar power, and its practitioners were
"swayed" — the hubris theme again. The Arcanist's Workbench crafts **Ancient Gateways** and
**Portal Fragments**, so portals are arcane technology.

**Arcanists** are a named historical group who "once sought the aid of more industrial diggers to
locate rich pockets deep underground" (`Rock_Gem_Emerald`) — they had a society and a labour market.

**Essences** are the game's elemental substance system. **Six kinds, seven items** — Life ships a
Concentrated variant: [asset, re-verified 0.7.2]

| Essence | Item id |
|---|---|
| Essence of Fire | `Ingredient_Fire_Essence` |
| Essence of Ice | `Ingredient_Ice_Essence` |
| Essence of Life | `Ingredient_Life_Essence` (+ `_Concentrated` = Greater) |
| **Essence of Lightning** | `Ingredient_Lightning_Essence` |
| Essence of the Void | `Ingredient_Void_Essence` |
| Essence of Water | `Ingredient_Water_Essence` |

**The essence *items* include no Air or Wind.** [gap, holds in 0.7.2] Lightning is the storm element
among them. But do not read that as the world having no Wind element — it has one, and the gap is in the
item roster rather than in the cosmology. See *"Four rosters"* below.

#### Essence, element, rune — three layers, and the game defines the third itself

**Corrected 2026-10-21.** This section used to end *"There are no **runes**"* [gap]. That was true of
0.5.9 and is false of 0.7.2: `server.lang` now carries **54** occurrences of *rune*, an entire
`Server/Item/Items/Rune/` tree, a crafting bench category *"Runestones"*, and a station called the
**Runebinder's Lattice**. [asset, 0.7.2]

The game supplies its own definition, and it is the most useful lore sentence found so far for this
project:

> *"Runebinders once sought to mimic Avatar powers through the use of Runes — a form of **harnessed
> symbolical energy imprinted upon stone**."* — `customUI.abilityBenchIntro.bullet1` [asset, 0.7.2]

Every word there places runes **below** essence:

- ***symbolical*** — a rune signifies; it is not the thing it names. The wikis say the same
  ("natural forces are *represented by* runes") but this is first-party.
- ***harnessed*** — captured and directed, therefore derivative of something that existed first.
- ***imprinted upon stone*** — an artifact somebody made.
- ***sought to mimic Avatar powers*** — mortal craft imitating higher power, which is **exactly** the
  framing §3 already records for the arcane arts (*"the closest mere mortals have ever come to achieving
  Avatar powers. Had they mastered them sooner, perhaps they would not have been so easily swayed"*).
  Runebinding is another instance of the hubris theme, not a separate magic system.

So the hierarchy is:

| Layer | What it is | Who owns it |
|---|---|---|
| **Essence** | world-substance — *"five magic essences that compose life on Orbis"* | Orbis / Gaia |
| **Element** | the natural force it expresses | the world |
| **Rune** | harnessed symbolical energy imprinted on stone, to mimic Avatar power | **mortals** |

**For questline writing this is the useful part:** a rune is a thing a character *makes*, and making
one is an act of imitation with a cautionary history attached. An essence is a thing the world *is*.
A story about runes is a story about ambition; a story about essence is a story about place.

#### Four rosters, no two identical

The single most load-bearing fact in this section, and the reason a roster argument went wrong twice:
**Hytale ships at least four overlapping elemental taxonomies.** [asset, 0.7.2]

| Roster | Members | Keyed to |
|---|---|---|
| **Cosmological essences** | Fire, Water, Lightning, Wind, Earth | Gaia's five pearls — *"compose life on Orbis"* |
| **Essence items** | Fire, Ice, Life, Lightning, Void, Water | what a player can carry |
| **Ability elements** | Earth, Fire, Lightning, Water, Wind | `DamageCause` — exactly these five, verified exhaustively |
| **Elemental Circles** | Earth, Fire, Frost, Poison, Sand | monument prefabs (§6) |

No two are the same list. Poison and Sand are circles but not essences; Ice and Life are items but not
cosmological; Wind and Earth are cosmological but not items. **So "which elements does Hytale have" has
no single answer from the jar — only "keyed to what".** When the assets offer four answers, lore is the
only thing that can adjudicate, which is why `CLAUDE.md` § "Lore first" puts it above the jar.

For the residue network's use of this, see
`../superpowers/specs/2026-10-20-residue-terrain-derivation-design.md` §3.

Essence lore treats magic as something that *accumulates in living things* — and the drop tables agree,
which makes this an economy and not only a theme. Essences come from creatures and crops, never from
ground: Fire has **20** NPC drop tables (Emberwulf, Firesteel golem, Burnt and Incandescent skeletons,
Magma slug/snail), Ice **12**, Void **12**, Life **1** plus 64 crop tables — and **Lightning has 1**
while **Water has none at all**. [asset, 0.7.2] The full consequence for terrain-derived residue is
worked through in `../superpowers/specs/2026-10-20-residue-terrain-derivation-design.md` §5
*"Terrain is the first arrow of a two-arrow chain"*.

> *"Even fish have a natural affinity for magic. As water filters through their gills, the
> concentration grows."* — `Ingredient_Water_Essence`
>
> *"A form of overflowing life energy found in abundance around plant life on Orbis. In great
> enough concentrations, perhaps it could even bring forth sentience where it was absent before."*
> — `Ingredient_Life_Essence`

**Vocabulary check — re-measured 2026-10-21 against 0.7.2.** `server.lang` is now **11,507** lines,
up from 10,235, and two of the original verdicts have flipped. [asset, 0.7.2]

| Word | 0.5.9 | 0.7.2 | |
|---|---|---|---|
| *leyline* | 0 | **0** | still ours to use |
| *resonator* | 0 | **0** | still ours to use |
| *attune* | 0 | **0** | still ours to use |
| *sigil* | 0 | **2** | **taken** — `Ingredient_Enkindled_Resource` is the "Enkindled Sigil". **Vacated 2026-10-21**: we renamed off it |
| *lodestone* | — | **0** | **ours** — adopted 2026-10-21 for the two trial proofs; 0 in lang *and* 0 in 68,759 asset names |
| *coursing* | — | **0** | **ours** — adopted 2026-10-21; clean in both surfaces |
| *standing* | — | 7 | ours, with a caveat: 7 lang hits and 1 asset hit, all lowercase prose, no content named this |
| *keystone* | — | **0 / 4** | **taken, and invisible to this table's old method** — see below |
| *rune* | 0 | **54** | **taken** — the runestone system |
| *crystal* | 89 | 85 | theirs |
| *storm* | 45 | 46 | theirs |
| *essence* | 17 | 11 | theirs |
| *arcanist* | 3 | 5 | theirs |
| *arcane* | 3 | 2 | theirs |
| *lightning* | 2 | 4 | theirs |
| *thunder* | 2 | 2 | theirs |

**Three words remain unclaimed**, down from five. A `[gap]` on vocabulary is the fastest-decaying claim
in this document, because each release can take a word, and nothing warns us — we find out by reading a
design that used it.

**`sigil` — RESOLVED 2026-10-21 by renaming.** `stormseeker` shipped `SIGIL_FLOWING` and
`SIGIL_ANCHORED` as both `ResourceId`s and item ids, chosen when `sigil` had **zero** occurrences.
0.7.2 ships the *"Enkindled Sigil"*, so a word picked precisely because the game did not use it
became a word the game does use. They are now the **Coursing Lodestone** and **Standing Lodestone**;
`sigil` appears nowhere in the source tree. One correction to the record: this paragraph previously
called ours *"player-facing item names"*, and they were not — no asset or `server.lang` entry ever
defined them, so the collision was prospective rather than live in anyone's inventory. That is why
the rename cost nothing: there was no shipped item to migrate.

**The method behind this table was incomplete, and `keystone` is the proof.** Every verdict above was
measured against `server.lang` alone. `keystone` has **0** occurrences there — and **4** in the asset
namespace, all Void content (`Void_Anomaly_Crystal_Keystone` as an NPC role, drop list, group and
spawn marker). It is invisible to a `server.lang` check because its display name is
`npcRoles.Void_Anomaly_Crystal.name = Void Crystal`; the word never reaches a player. That matters
because the Stormseeker narrative had already chosen *Keystone* as the replacement for *sigil*, on
the strength of a check that could not see it.

> **A vocabulary check must read both surfaces: `server.lang` (11,507 lines) and the asset namespace
> (68,759 entries).** The first tells you what a player can read; the second tells you what Hypixel
> has built. A word clean in the first and taken in the second is safe *today* and becomes a
> collision the moment they give it a display name — which is a change we would not notice.

This is a different failure from the stale-`[gap]` problem below. That one is about *when* a claim was
measured; this one is about *what was measured at all*. Both produce a confident zero.

**One further collision to watch, not yet a problem:** *lattice*. 0.7.2 names a player-facing station the
**Runebinder's Lattice**, and the residue design's candidate generator is also called a lattice (that
spec's §4, *"the lattice proposes, terrain disposes"*). Ours is internal geometry and theirs is a
building, so nothing breaks today — but the word is no longer neutral, and any player-facing residue
text should avoid it.

---

## 4. Storms

Storms are already a developed part of this world. [asset]

**The lore of storm magic** is written on `Ingredient_Lightning_Essence`:

> *"Raging storms have proved attractive to many creatures over the centuries. Though the true
> source of such magic is nowhere to be found, the elementals born from its lingering traces still
> wander the lands."*

Three things are canon in that one sentence: storms carry magic; **the source of that magic is
unknown**; storm elementals are *born from residue*, not from any living power. That unknown source
is an open door — it is the kind of thing a questline can be about without contradicting anything.

**Storm weather.** Of 87 weather definitions: `Zone1_Storm`, `Zone2_Sand_Storm`,
**`Zone2_Thunder_Storm`**, `Zone3_Snow_Storm`, `Zone4_Storm`, `Zone4_AshWastes_Storm`,
`Zone4_Swamp_Storm`, `Skylands_Rapid_Marsh_Stormy`. **Thunder exists only in Zone 2.**

**A storm material chain already exists**, and nothing in the game currently gates it behind a story:

- **Storm Hide** → **Storm Leather** (at a Tannery) → Storm Leather armour set
- **Stormsilk Scraps** → **Bolt of Stormsilk** → Stormsilk armour set
- **Essence of Lightning**
- **Storm Thistle** (`Plant_Crop_Stamina1`) — a stamina crop

---

## 5. Creatures

**Elementals** are a first-class NPC family: [asset]
`Dragon_Fire`, `Dragon_Frost`, `Dragon_Void`, `Golem_Crystal`, `Golem_Firesteel`,
`Spirit_Ember`, `Spirit_Frost`, `Spirit_Root`, **`Spirit_Thunder`**.

Golem variants are named in text as Earthen, Ember, Frost, Sandswept, **Thunder**, Firesteel, and
Void Guardian.

**`Spirit_Thunder`** — the thunder elemental — deserves its own note:

- A `Variant` of `Template_Spirit`, `MaxHealth: 249`, `IsMemory: true`, `MemoriesCategory: Elemental`.
- Its drop list yields **`Ingredient_Lightning_Essence` ×2–3**, and it is the **only source of
  Lightning Essence in the entire game** (verified by scanning every file under `Server/Drops/`).
- It spawns **only in Zone 2**, through `Zone2_Elemental_Circle_Tier{1,2,3}` spawn beacons
  (`BeaconRadius` 10, `SpawnRadius` 12, `MaxSpawnedNPCs` 1, spawn state `Alerted`).

**Intelligent races** [asset]: Bramblekin, Elf, Feran (+ Cub), Goblin (+ Duke, + Ogre), Hedera,
Klops, Kweebec (Ancient / Rootling / Sapling / Seedling / Sproutling — five life stages),
Outlander (+ Brute), Saurian, Slothian (+ Elder, + Kid), Trork (+ Exceptional, + Hatchling), Tuluk.
The one shipped boss is `Golem_Guardian`.

---

## 6. Places

**Zones 1–4, each tiered.** [asset] Zone 1 has Tiers 1–3 plus Shore, Shallow Ocean, Spawn and
Temple; Zone 2 Tiers 1–3 plus Shore and Shallow Ocean; Zone 3 Tiers 1–3 across Shore and Shallow
Ocean; Zone 4 Tiers 4–5. Also Zone 0, Skylands, Poisonlands, Void and Portals.
Instances: Creative Hub, Goblin Dungeon, **Forgotten Temple**.

**Monuments** are the world's authored structures, and their categories are a map of how Hytale
places content: `Challenge/`, `Encounter/` (per zone, plus City Ruins and City Oceans),
`Incidental/`, **`Story/`** (`Start`, `Story_gate`, `Forgotten_temple`), and
**`Unique/Elemental_Circles`**.

### Elemental Circles — and the hole in them

Elemental Circles are unique monuments where elementals gather. Shipped variants: [asset]

> **Earth** (whose prefabs are named *Druid_Circles*) · **Fire** · **Frost** · **Poison** · **Sand**

**There is no Air, Thunder, or Storm circle.** [gap, **re-verified 2026-10-21 against 0.7.2** — the
shipped variants are still exactly these five] The monument family exists, the naming convention exists,
the spawn-beacon convention exists — and the storm element, alone among the elements with both an
essence and a spirit, has no circle. Two releases on, it is still the mod's opening.

**Note what this roster is, though.** Earth, Fire, Frost, Poison, Sand is the **fourth** of the four
elemental taxonomies catalogued in §3, and the least cosmological of them: Poison and Sand are not
essences in any sense, Frost stands where the cosmology puts Water, and Water, Lightning and Wind have
no circle at all. So a Storm Circle would not be "completing the set" — there is no set. It would be
asserting that the storm element deserves a monument, which is a claim the fiction has to earn rather
than inherit.

**Wind architecture already exists** to build one from: a full `Temple_Wind` decorative block set
(candles, chandelier, chest, door with open/close animations), the `Furniture_Temple_Wind_Statue_Gaia`
— *"Statue of a Silent Deity"* — and a `Block_Spawner_Wind` ("Wind Spawner"). There is a portal
key for **"Fragment: Orbis — Windrider Valley"**. [asset]

**And none of it is placed.** [asset, verified 2026-08-25] The 84 `Temple_Wind` assets are a build
palette with **no shipped structure using them** — no prefab, no worldgen entry. `Statue_Gaia` exists
in exactly two variants, `Temple_Dark` and `Temple_Wind`, both under `Unique/`; the Dark one has dark
temples to appear in, the Wind one has nowhere. This matters for more than availability: an asset
that already dresses every ruin in the game cannot carry weight in a story, and these carry none yet.
A mod is free to be their first user.

---

## 7. Systems a story can hook into

**Memories — "The Heart of Orbis".** [asset] A bestiary/collection progression with 18 categories:
Abyssal, Avian, Critter, **Elemental**, Feran, Freshwater, Goblin, Kweebec, Livestock, Mythic,
Outlander, Predator, Reptile, Scarak, Trork, Undead, Voidspawn, Voidtaken. NPCs flagged
`IsMemory: true` feed it; it has capacity, levels, and unlock tiers that gate crafting recipes
(level 4 unlocks Ancient Gateway and Portal Fragments at the Arcanist's Workbench).

**Objectives.** Hytale ships a native questline framework. See §4.1 of the capability audit.

**Trigger Volumes.** Region-triggered event scripting with an in-game editor, shipped Update 5.

---

## 8. Open questions this brief cannot answer

- What the curse *is*. [gap] Announced, unshipped.
- Where Varyn, Tessa and Kyros sit relative to Gaia. [gap]
- Whether the "true source" of storm magic is authored canon the studio is holding back, or genuinely
  open space. This is the single most load-bearing unknown for any storm-themed questline: building
  a questline that answers it risks being contradicted by a later chapter.
- Zone names. The shipped assets identify zones only by number and tier; no display names appear in
  `server.lang`. [gap]
