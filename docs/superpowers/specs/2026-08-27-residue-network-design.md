# Residue as a network — currents, Circles, and the needle

**Status: structure decided, numbers open.** This supersedes the geometry of
`2026-08-25-act-iii-residue-literacy-design.md` while keeping its principles. The open items at
the end are tuning values, not unresolved structure — which is the difference between this
document and its predecessor.

---

## 1. What changed, and why

The 2026-08-25 spec modelled residue as a **scalar density field derived from each
`Environment`'s storm frequency**. That followed from the measurement that storms are a property
of place. It was a sound inference and it is now replaced, because the density field could not
answer a question the design turned out to depend on: *which way do I walk?*

A scalar cannot carry direction. That is not a tuning failure — the information is absent from
the representation. Every navigation idea built on it either failed or smuggled in a second
mechanism.

The replacement is the model this project started with, before v4.0: **residue runs in
currents.** Invisible lines flow through the world, cross each other, and the crossings are the
Circles. This was v3.1's structure, recorded in `docs/stormseeker/archive/design.md`:

> **Leylines** — Always exist in the world.
> **Elementally Charged Crystals** — Always visible/collectable. Spawn **exclusively within
> leyline influence radius**. Density scales with proximity to leyline convergence points.

**v4.0 deleted this along with Leyline Sight, and only Leyline Sight deserved deleting.** The
granted, toggled, permanent perception violated *nothing is granted*; the geometry never did.
The two were removed as one unit because they arrived as one unit. This spec un-couples them:
the geometry returns, the toggle stays dead.

### Consequences for the prior spec

| Prior | Now |
|---|---|
| **D1** nothing is granted | **Stands.** Strengthened — residue is world structure present from first login, for every player, gated on nothing. |
| **D2** core protection gates the action | **Removed.** There are no cores. See §3. |
| **D4** density derived from environment storm weight | **Superseded.** Density derives from proximity to a current. Storm frequency may still bias where currents run — that is open, not assumed. |
| **O1** what does the signal look like | **Largely dissolved.** See §4. |
| **O2** does the field need seed noise | **Answered by the geometry.** Local variation is distance-to-current, not noise. |
| **O3** cluster size, depletion, regrowth | **Dissolved.** Cultivation replaces finite harvest; there is no depletion rate to get wrong. |
| **O4** does Act III need a completion condition | **Still open.** Carried forward as N5. |
| **O5** how is the literacy taught | **Partly answered.** See §5. |
| §6 sub-project list | **Replaced** by §7. |
| "thistle lean is not buildable" | **Stands.** Shipped blocks cannot carry our behaviour. |

The 0.6.1 storm-probability table in that spec's §2 remains correct and is not restated here.

---

## 2. The model

Three kinds of thing, in increasing rarity.

**Currents.** Invisible lines that flow through the world. One per element. Only the **storm**
current is implemented; the other five are named by the base game and are not built (§6).
A current has a *direction* — it flows — and that is load-bearing, not flavour (§4).

**Circles.** Where the storm current crosses **itself**. These are the nexuses: places of
concentrated storm power, and where crystal density peaks. Findable by literacy alone (§4).

**The Grand Convergence.** The single rare point where **all** currents meet. An ancient
workshop stands there. It is where the Stormseeker frame is forged, and it is the only place in
the design that refers to elements we are not building.

### Cross-element crossings are not modelled

A Storm–Frost crossing is not a place. Considered and deliberately excluded: the combinatorics
are large, and no gameplay has been designed for them. This is a decision to revisit when
someone can name what happens at one — not a gap.

It also removes the only quadratic term in the system. With same-element crossings only, each
current is tunable **independently**: questline #2 sets frost's spacing without knowing storm's,
and the finished web grows linearly in the number of elements. The Grand Convergence is the
**sole** shared geometric constraint across all questlines.

---

## 3. Decisions

**R1 — Residue is world structure, not a reward.** Present from first login, identical for
every player, gated on no quest state. Nothing is unlocked, toggled, or granted at any point.

**R2 — There are no cores.** The irreversible geode core and its action gate are removed
entirely. Nothing in the residue system is a one-per-site consumable that a player can destroy
for others.

**R3 — Crystals are cultivable.** Harvest is not the only supply. This removes the grind risk
the prior spec flagged as its most dangerous open number: there is no depletion rate and no
regrowth rate, so neither can be tuned wrong.

**R4 — Circles are self-crossings of one current.** Not authored individually. Their
distribution is a property of the network, so tuning the network tunes the content density.

**R5 — The Grand Convergence is authored and shared.** It cannot be derived by intersecting
implemented networks, because five of the six do not exist. It is a fixed point that the storm
current is *routed through*. Every future elemental current must route through the same point.

**R6 — Storm materials have a recurring use, not only a terminal one.** The frame is forged
once. Cultivation would be pointless after that if the frame were the only sink. See §5.

---

## 4. Navigation — two mechanisms, deliberately incomplete alone

The player perceives the network **only** through what it produces. There is no residue
rendering, no overlay, no sight mode.

**Literacy — where the line is.** Crystals grow near currents, denser toward Circles. Reading
where crystals are thick *is* reading the network. This is a scalar and it resolves position
only: it tells you that you are near a current, and that density is rising. It cannot tell you
which of the two directions along a current leads anywhere.

Because a Circle is a **local maximum** of density, it is reachable by literacy alone — walk
uphill. No sense of direction is required. **Circles are therefore findable on day one, by
anyone, with no tool.**

**The needle — which way the current flows.** A crafted, consumable instrument of crystallised
residue that aligns to the flow. It answers exactly the question density cannot, and nothing
else: no distance, no bearing to a target, no function off-current, no knowledge that Circles
or the workshop exist.

This division is the design, not an implementation detail. **A tool that pointed at the
destination would end the literacy** — the player would follow an arrow and stop reading the
world, which is what happened to Nether navigation in Minecraft once eyes of ender existed.
Keeping the needle strictly directional means the crystals remain load-bearing at every step:
the needle orients you on a line you still have to find, and re-find, by reading.

**The Grand Convergence is upstream.** The current is strongest where all currents meet, so
"follow the flow up" is always the answer. That makes a rare, required destination reachable by
a method the world teaches, with no quest text — which is what canon keeps asking for.

So the progression is: **read your way to a Circle → recover the needle's method there → follow
the current upstream to the workshop.** Two tiers of place, two tiers of navigation, and the
tool is earned by the exact skill it then extends.

---

## 5. The needle, and what storm materials are for

### Crafting gate — mechanism verified against 0.6.1

Hytale has learned recipes, and they are the right fit. Recipes are declared inline on the
output item as a `"Recipe": { … }` block carrying a **`KnowledgeRequired`** boolean. Teaching is
a consumable item using the `LearnRecipe` interaction type:

```json
"Consumable": true,
"Interactions": { "Primary": { "Interactions": [
  { "ItemId": "<target item>", "Type": "LearnRecipe",
    "Next": { "Type": "ModifyInventory", "AdjustHeldItemQuantity": -1 } } ] } }
```

The server tracks known recipes per player (`UpdateKnownRecipes` packet), and there is a shipped
`Items.Recipes` category with a `Recipe.blockymodel` and icon set to build on.

Three constraints, all verified:

- **`KnowledgeRequired` is valid only on bench recipes.** The validator's own message:
  *"KnowledgeRequired in recipe can't be set for non crafting recipes."* A gated needle cannot be
  hand-crafted from the inventory; it needs a bench. Given the fiction, acceptable.
- **The field is a primitive `boolean`, defaulting to `false`.** Set it explicitly.
- **This is an established convention, not an experiment.** 19 shipped items set it true —
  including the pies and the entire `Armor_Steel_Ancient` set. Ancient equipment whose method
  must be recovered before it can be made is already the game's idiom.

### Where the knowledge comes from

**A `Recipe_` item recovered at a Circle.**

This is forced by a circularity: the needle is what reaches the workshop, so the needle's recipe
cannot be *in* the workshop. Circles are the correct source because they are findable by
literacy alone — the player arrives having read the world, and is rewarded with the means to
read it further.

**Rejected: `RequiredMemoriesLevel`.** The recipe schema offers it, and the Memories system is
thematically close — you *catch* lingering things and the record notes where you found them,
and `Spirit_Thunder` / `Golem_Crystal_Thunder` ship as memory-bearers. But the level is
**global and category-blind**: one `Set<Memory>` per player, one `MemoriesAmountPerLevel`
(`[10, 25, 50, 100, 200]`), and the only consumer of `getRequiredMemoriesLevel` is
`CraftingManager` checking one integer. `MemoriesCategory` is read solely by the journal UI and
the role builders. So gating on it means *"has caught 25 creatures anywhere"* — mostly zombies
and birds — which would deliver the needle at a moment unrelated to anything the player did with
storms.

Noted for later, not now: `MemoriesConditionInteraction` carries a `Map<Integer, Integer>`. **If**
those keys identify specific memories rather than levels, gating on *having recorded
`Spirit_Thunder`* becomes possible and would be storm-specific. Unverified. Do not design on it
until someone checks.

### The materials loop

Cultivation creates supply that grows over time. A questline consuming a fixed amount once would
leave that supply pointless the moment it ended — a farm nobody visits twice.

The consumable needle is the recurring sink, and the two uses form a cycle rather than a list:

> cultivate → craft needles → travel further → find more Circles → more sites to cultivate

The one-off (the frame) sits **inside** that cycle instead of terminating it. Any further use
for storm materials should be judged against whether it strengthens this loop or merely adds an
item.

---

## 6. Scale

The elemental roster is not open-ended and does not need guessing. The base game ships five
Elemental Circles — **Earth, Fire, Frost, Poison, Sand** — and ships **no Storm circle**. That
absence is the hole this questline fills, so the plausible complete set is **six** currents.

(Caveat: essences are a different axis — seven exist, including Life, Void and Water, which have
no circles. Whether currents follow circles or essences is a *choice*, and it moves N from 6 to
~7. It does not change the shape of the budget.)

Because cross-element crossings are not modelled (§2), total Circles across the finished web
grow **linearly**: six currents produce roughly six times the storm current's self-crossings, not
twenty-one times. Storm's spacing can therefore be tuned to feel right today without a hidden
multiplier waiting in questline #4.

The one number that must be chosen with the whole web in mind is **storm's self-crossing
frequency**, since it is the per-element figure everything else will imitate.

---

## 7. Implementation shape

Sub-projects in dependency order. Each gets its own spec and plan.

1. **Residue network (`:core`)** — current geometry, the Grand Convergence locator, and a pure
   `residueDensityAt(position)` / `flowDirectionAt(position)` query pair. Places nothing,
   persists nothing, no worldgen dependency.
2. **Storm current (`:quests:stormseeker`)** — the storm instance of (1): its parameters,
   its Circles as content, its siting rules.
3. **World expression (`:mod:hytale`)** — crystals near currents, density scaled by (1).
   Unblocked: particle emission is available to plugins at arbitrary positions (§8).
4. **The needle** — item, bench recipe with `KnowledgeRequired: true`, the `Recipe_` item that
   teaches it, and its Circle placement.
5. **The workshop** — the Grand Convergence structure and the frame recipe.

### Module boundaries

The boundary rule is mechanical: *does this know about a specific questline?*

**The network framework and the Grand Convergence belong in `:core`.** The convergence is shared
world state that every elemental questline must route through — if `:quests:stormseeker` owned
it, questline #2 would have to depend on Stormseeker, which `:core:checkNoQuestlineDependency`
exists to prevent. The convergence does not know about storms. The storm current does.

**Worldgen is not available to any of this.** Deferral #18: amending the shipped world is a
V1-only capability via `WorldGenModifier`, on the generator being retired. Re-verified 2026-08-27
against 0.6.1 — the trigger has **not** fired (§8). A derived virtual network sidesteps this
entirely, which is why (1) is a query and not a generator.

---

## 8. Verified against 0.6.1 (2026-08-27)

Recorded so none of it is re-derived, and so a later reader knows what was checked rather than
assumed.

- **Crafting:** `KnowledgeRequired` exists; 19 shipped items true, 168 false; primitive default
  `false`; valid only for crafting-bench recipes. `LearnRecipe` is a first-class interaction
  type. Other gating axes exist and are unused here: `RequiredTierLevel`, `RequiredAugmentTags`,
  `DiagramCrafting`.
- **Memories:** global level from `[10, 25, 50, 100, 200]`; **333** NPC assets set
  `IsMemory: true`, across **18** categories, of which Elemental holds 11. Category is
  display-only — read by the journal UI and the role builders, by nothing that computes a level.
- **Worldgen still V1:** boot logs `HytaleWorldGenProvider{name='Default'}` and `/worldgen`
  exposes only `benchmark` and `reload`, despite 0.6.1 adding 20+ WorldGen V2 graph nodes.
- **Particles — resolved 2026-08-27.** `ActionSpawnParticles` gained a `float scale`, but it is
  an **NPC action** and `emitWorldParticle` is private, so the capability appeared NPC-locked. Its
  bytecode's terminal call is **`ParticleUtil.spawnParticleEffect`**, a `public static` utility
  taking a raw position; `ActionSpawnParticles` is merely one caller. Verified on a live server
  with a connected client via `/residueprobe`: 225 dispatches, zero throws, three visually
  distinct effects confirmed by the operator. **A plugin can emit any shipped particle at any
  position, with no NPC involved.**
- **Points (new in 0.6.1):** `PointEntry` carries id, world, position, rotation, name, enabled,
  shape and a `Map<String,String>` of tags; it persists, travels with prefabs
  (`PointPrefabContributor`), and `SensorPoints` queries by range and tag. Not used by this
  design — noted because it is the obvious tool if the Grand Convergence ever needs authoring
  support.

---

## 9. Open

- **N1 — Storm's self-crossing frequency.** How many Circles per world. The per-element figure
  every future current will imitate (§6).
- **~~N3~~ — closed 2026-08-27.** A plugin can emit world particles at arbitrary positions via
  `ParticleUtil.spawnParticleEffect`. Sub-project 3 is unblocked. See §8.
- **N2 — Needle cost and yield.** How many crystals per needle, and how far one gets you. Sets
  whether the loop in §5 is a rhythm or a chore. Against play data.
- **N4 — Does storm frequency bias where currents run?** The 0.6.1 storm table is a real signal
  about place and it would be a shame to waste it, but nothing currently requires the coupling.
- **N6 — `densityAt` is a plateau, so a Circle is not a local maximum of density.** §4 states the
  opposite as load-bearing: *"Because a Circle is a local maximum of density, it is reachable by
  literacy alone — walk uphill."* The shipped implementation is `1 − d/R` against the nearest
  segment, which is **exactly 1.0 at every point on the network** — verified at a Circle and at
  sixteen arbitrary on-current vertices. Walking uphill reaches a current and then stops; it never
  distinguishes a Circle. Three ways out: give `densityAt` a Circle-proximity term (makes §4 true
  and keeps sub-project 3 cheap); revise §4 so crystals mark currents uniformly and Circles are
  found by *following* one; or accept that sub-project 3 must call `circlesWithin` per placement
  region — which is the O(n²) per-tick use §7 defers a spatial index for. **Decide before
  sub-project 3.** Found by the whole-branch review 2026-08-27; no per-task review could see it.
- **N7 — the Z-filter regression hole in `circlesWithin` is open.** The production fix for
  non-finite bounds landed, but the test meant to prove Z filtering is independent of X does not
  kill the `maxZ → maxX` mutation — proven against a compiled mutant, not by inspection. At seed 1
  the crossing is `c=(371.16, 260.65)` and both the real and substituted upper bounds sit above
  `c.z()`, so the swap changes nothing. A correct test must **select** a crossing where the two
  bounds straddle `c.z()`; it cannot be written blind to the geometry. Production behaviour is
  unaffected — this is a regression hole, not a live defect.
- **N5 — Does Act III need a completion condition at all,** now that its content is world
  structure rather than a granted state? Carried unchanged from the prior spec's O4.

---

## 10. Provenance

Design conversation of 2026-08-27, following the Hytale 0.6.1 update the same day. Engine facts
in §5 and §8 were read from the shipped assets and `HytaleServer.jar` of
`0.6.1` / `5097cd9e1099a0af639b359b453e4b117fe9f2a0`, on a live server, not from the patch notes.
Supersedes the geometry of `2026-08-25-act-iii-residue-literacy-design.md`; that document's
measurements, its forecast dead-end (§5), and its principles remain in force.
