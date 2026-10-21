# The trial proofs are Lodestones, not Sigils — rename decided and executed

**Status: DECIDED and IMPLEMENTED 2026-10-21.** Replaces `sigil` throughout the Stormseeker
questline with `lodestone`, adds the material class the two items never had, and migrates saved
progress. Build green: `DOCS_VERDICT: GREEN`, `CENSUS_VERDICT: GREEN | 356 tests | 0 failures`,
`COVERAGE_VERDICT: GREEN | 4/4 modules | 0 exempt`.

---

## Why

`sigil` was chosen when it had **zero** occurrences in `server.lang`. Build 0.7.2 ships the
*"Enkindled Sigil"*, so the word stopped being invented and became colliding — asserting a
connection to Hypixel content we do not control, which is the exact cost an invented word was
chosen to avoid.

**One correction to the record.** The setting brief described ours as *"player-facing item names"*.
They were not: no asset and no `server.lang` entry ever defined them, so the collision was
prospective rather than live in anyone's inventory. That is also why this cost nothing — there was
no shipped item to migrate, only internal ids.

## What the names are, and why a pair

**Coursing Lodestone** (Flowing Trial) and **Standing Lodestone** (Anchored Trial).

A lodestone is naturally magnetised and has two poles. The Flowing Trial scores movement against its
own previous direction — it proves you move *with* the storm, so its stone is the pole that
**draws**. The Anchored Trial requires 40 stationary ticks — it proves you *hold*, so its stone is
the pole that **stands**. The blade needs both because calling lightning and holding it are not the
same act.

This replaces the left-half/right-half structure inherited from the Thunderfury lineage note. Two
halves are a lock; two poles are a reason.

### Why not Keystone, which the narrative had already chosen

v4.0 recorded *"sigils becoming Keystones that are tiers of the monument"*. Two problems:

1. **A Keystone is a structure left standing in the world; these are carried and consumed.** Class D
   says *"Each Keystone is a tier of the Circle"*, and Class D is **absent** from the "Stormseeker is
   forged from" table. The two trial proofs go into the sword, so they were never Class D. They are
   now **Class F — Lodestones**, which is the class they had been missing entirely.
2. **`Keystone` is taken, and the vocabulary check could not see it.** 0 occurrences in `server.lang`;
   **4** in the asset namespace, all Void (`Void_Anomaly_Crystal_Keystone` — NPC role, drop list,
   group, spawn marker). Invisible to a lang check because its display name is `Void Crystal`. And
   Void is, per the 2026-10-21 residue decision, a hostile incursion not native to Orbis — poor
   vocabulary for a storm Circle even when players never read the word.

### Vocabulary measured before adoption, against both surfaces

| word | `server.lang` (11,507 lines) | asset names (68,759) |
|---|---|---|
| `lodestone` | 0 | 0 |
| `coursing` | 0 | 0 |
| `standing` | 7 | 1 |

`standing`'s hits are all lowercase prose with no content named for it. Spelling is **lodestone** —
*lode* as in a vein of ore; "loadstone" reads as a typo on a player-facing name.

## The non-physical alternative, and why it is not this

A mark — tattoo, brand, scar — was considered. It cannot be the crafting token, because a mark
cannot go into a recipe. Two findings worth keeping:

- **The engine has the right slot and ships it empty.** `PlayerSkinPartType.SkinFeature` exists
  alongside `PlayerSkinPartTexture` and `PlayerSkinPart$Variant`, and
  `Cosmetics/CharacterCreator/SkinFeatures.json` is **2 bytes — `[]`** while all 28 siblings are
  populated (Haircuts 46KB, Overtops 46KB). Hypixel built the category and put nothing in it.
- **The shipped mechanism for a facial mark replaces the whole face.** `Face_Scar` is one of 24
  `Faces` entries, each a complete `GreyscaleTexture` on `Player_Face_Detached.blockymodel`. Granting
  a mark that way would overwrite the player's chosen face.

So a mark is a **spike, not a plan**: unproven whether a mod can register a `SkinFeature`, whether
one can be granted at runtime rather than chosen at creation, and whether it renders additively. If
it ever proves out, the place for it is the sign the trials leave *after* the stones are spent — the
proof already exists as state (`hasCoursingLodestone` / `hasStandingLodestone`), independent of any
item.

## Save migration — the only part with teeth

The `.properties` store keyed the two proofs as `sigilA` / `sigilB`. The new keys are
`lodestoneCoursing` / `lodestoneStanding`, and **`load` falls back to the legacy keys when the
current ones are absent**.

Without the fallback the rename would be **silent data loss, not a visible failure**: a pre-rename
file parses perfectly and simply lacks the new keys, so `load` would return both proofs as `false`,
`quarantine` would never fire, and the next disconnect would save that fresh state over a player's
completed trials.

Precedence is by **presence, not truth** — an explicit `lodestoneCoursing=false` beats a stale
`sigilA=true`, so a post-rename save is never overridden by a leftover. Migration is one-way: the
first save after upgrade drops the legacy keys.

Four tests pin it, and all four were shown to fail before the code existed: three were RED on the
rename alone, and the precedence test was killed by inverting the fallback order. Green-on-first-write
proves nothing, so each assertion was checked against a mutation that should break it.

## Scope

| Surface | Change |
|---|---|
| `quests/stormseeker` | ids, accessors, milestone enum, deny reason, objective id, 7 files renamed |
| `mod/hytale` | status/trial command output |
| `harness` | dogfood + harness tests |
| `core` | `LegendaryItemRole.SIGIL` → `PROOF_TOKEN` |
| `docs/stormseeker/stormseeker-canonical.md` | **Class F — Lodestones** added; forging table gains its row |
| `docs/setting/hytale-orbis-setting-brief.md` | vocabulary table + the both-surfaces method rule |

**`core`'s rename is cosmetic and that is itself a finding.** `LegendaryItemRole`,
`LegendaryItemIdentity` and `LegendaryItemPolicy` have **zero** consumers outside core's own four
test files — the third instance in two sessions of the heuristic `CLAUDE.md` states explicitly:
*grep its call sites; if the only callers are its own tests, it is a design, not a mechanism.* The
first was the questline framework, the second residue's `elementId`. The rename was free precisely
because nothing uses it, and the enum's three members are all Stormseeker's material classes sitting
in the engine-agnostic module.

## What this does not change

- No mechanic. The trials score exactly what they scored before; only the names moved.
- No shipped asset or translation key — none referenced `sigil`, and none defines the new items
  either. Display names still need authoring when these become real items.
- Class D Keystones are untouched and remain tiers of the Circle.
