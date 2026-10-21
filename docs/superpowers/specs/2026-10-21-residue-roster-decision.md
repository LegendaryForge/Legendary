# The residue roster — Fire, Water, Lightning, Wind, Earth, with Void apart

**Status: DECIDED 2026-10-21, as a reversible modelling default.** This is what our content keys to.
It is **not** a claim about Hytale's taxonomy, and it is deliberately cheap to revise — see
*"The mechanism that keeps this reversible"*. Vocabulary stays `essence`.

Settles §9 q5 of
[`2026-10-20-residue-terrain-derivation-design.md`](2026-10-20-residue-terrain-derivation-design.md),
whose precondition was discharged by
[`2026-10-21-residue-two-arrow-chain-decision.md`](2026-10-21-residue-two-arrow-chain-decision.md).
**That document remains a PROPOSED DIRECTION in every other respect** — terrain derivation itself is
not adopted here.

**No code changes.** `elementId` is an opaque `ResourceId` with zero production consumers; every
construction in the repository is in a test. This is documentation and future content.

---

## The roster

**Fire, Water, Lightning, Wind, Earth.** Void is handled separately as an incursion (§6). Ice and
Life are not members. `:core` today keys to the six shipped essence *items* (Fire, Ice, Life,
Lightning, Void, Water); that is what changes, in documentation.

**Lightning is in every roster Hytale ships, so Stormseeker is unaffected by this decision.**

## Why, on grounds that do not depend on current content volume

**Lore first.** Essence is defined first-party as *"five magic essences that **compose life on
Orbis**"* — *"Gaia and the Elemental Essences"*, concept art on Hytale.com by Hypixel technical
artist Thomas Frick. [lore-1p] The shipped item list is keyed to *what a player can carry*, which is
a different question from what essence **is**. Per [`CLAUDE.md`](../../../CLAUDE.md) § "Lore first",
lore adjudicates meaning and the jar does not.

**The engine's own exhaustive element taxonomy is the same five.** `Server/Abilities/Elements/`
ships exactly five one-line files — `Earth`, `Fire`, `Lightning`, `Water`, `Wind`, each
`{"DamageCause": "X"}` — and `DamageCause` is exhaustive across all shipped JSON. [jar] Two
independent surfaces, one of them first-party lore, land on one set.

Read that corroboration carefully, because §3 of the prior document earned the warning: those same
five files once corroborated a roster that was still wrong. Asset agreement cannot tell you *which
question* a list answers. Here lore establishes the question — what is essence — and the
`DamageCause` agreement corroborates **membership for the natural forces**. It is support, not proof.

## An argument used to take this decision, and withdrawn

An earlier draft weighed the rosters by how well shipped creature content corroborates each member,
and produced: cosmological five corroborated in **1 of 5** members (Fire alone — 20 droppers, 13
world-placed, 14 environments), against the shipped six at **4 of 6** (Fire 20, Ice 12, Void 12,
Life 1 + 64 crop tables). It was offered as the cost of choosing the five.

**It is withdrawn. That table measures build order, not taxonomy.** The decisive evidence is that the
game's two elemental layers are each incomplete, and incomplete in *opposite* directions:

| Layer | Members | What it is |
|---|---|---|
| `Server/Abilities/Elements/` | Earth, Fire, Lightning, Water, **Wind** | systems definition — **complete at five** |
| `Server/Item/Items/Ingredient/*_Essence` | Fire, **Ice**, **Life**, Lightning, **Void**, Water | content economy — no Wind, no Earth |

Wind and Earth are already first-class elements in the engine's exhaustive damage taxonomy; they
simply have no essence **item** yet. Ice, Life and Void are the reverse — items with no
`DamageCause`. That is the signature of a game under active development: the cheap systems layer is
complete, the expensive content layer exists where content was built. Fire has twenty droppers
because Zone 4's Crucible got built, not because Fire is more of an essence than Wind.

**Generalise it, because this will recur.** Hytale ships every 2–6 weeks and is still fleshing out
essences, damage sources and runes. **A content count is evidence about what has been built, never
about what the world is.** A roster argument that rests on one is measuring Hypixel's schedule.

## Our three modelling choices, stated as ours

These are not claims about Hytale. Each carries what it is anchored to and what would tell us it
moved.

| Choice | Anchored to | Falsifier / confirmer |
|---|---|---|
| **Wind and Earth are fields** | both ship as `DamageCause` — the engine names them elements [jar] | a Wind or Earth essence **item** shipping — confirms, needs no change |
| **Ice is modelled inside Water** | Water's own text: *"represents the magic of water and ice"* [jar]; §5 already sites Water at ice and snow | Ice gaining terrain-bound placement distinct from glacial Water |
| **Life is not a field** | 1 NPC dropper against **64** crop tables; *"found in abundance around plant life on Orbis"* [jar] — present wherever vegetation is, so a constant, and a field is the whole mechanism | Life gaining wild, terrain-bound sourcing |
| **Void is an incursion, not ground** | §6 lore, plus spawn data: droppers across **16** environments in three zones with no terrain character [jar] | Void droppers becoming terrain-bound |

**Ice is the one worth being explicit about**, because it has 12 shipped droppers and dropping it
from the roster must not orphan them. It does not: Ice's droppers live in glacial environments, so
under the two-arrow chain the **Water field explains them**. Subsumed, not ignored.

## Where we are theorizing, and why that is acceptable

Hypixel keep the lore deliberately open-ended, and where it is silent we invent — that is
`CLAUDE.md` § "Lore first" corollary 3, not a liberty taken here. Wind and Earth residue is the
clearest instance: fields for two elements the engine names but has not yet given an item, a creature
or a drop table.

The posture, stated by the operator when taking this decision:

> Where something is not concretely set in the game, theorize plausibly and build on it. If it gets
> fleshed out later and the theory was off, re-adjust — and try not to be too far off the mark.

Two things follow, and this document applies both rather than describing them:

- **Anchor every invention to the nearest first-party thing.** The closer the anchor, the smaller the
  expected correction. Wind and Earth are anchored to an exhaustive engine taxonomy, which is about
  as close as an anchor gets without the item existing.
- **State what would falsify it**, so a correction is triggered by a release rather than noticed by
  accident. That is the right-hand column above.

The existing provenance tiers (`[measured]`, `[jar]`, `[lore-1p]`, `[lore]`, `[proposed]`) do not
distinguish an arbitrary design choice from an anchored extrapolation, and both land in `[proposed]`.
Formalising a tier for the latter is a change to the project's documentation conventions and is
**left as a separate decision**; the practice is applied here regardless.

## The mechanism that keeps this reversible

**`elementId` stays an opaque `ResourceId`. Do not add an enum, a list, a default, or validation.**

That is the current shape, and it was previously read as a gap — four specs and four pull requests
described element identity as *"baked into `:core`'s API"* and derived urgency from it. Verified
2026-10-21: no enum, no list, no validation, and every construction in the repository is in a test.
Zero production consumers.

Given an upstream that ships every 2–6 weeks and is still adding elements, that openness is the
**correct design, not an omission**. An opaque id makes a roster change a documentation edit. An enum
with validation would make it a breaking API change plus a data migration — converting a decision
that costs nothing today into one that costs a release.

So this is recorded as a deliberate posture, specifically so that a later tidy-up does not "improve"
it by closing the thing that makes the roster safe to decide now.

## Revisit triggers — game events, not dates

1. **A Wind or Earth essence item ships.** Confirmation; the item layer caught up. No change needed,
   and worth recording as the theory landing.
2. **First-party lore names a sixth cosmological essence**, or revises the five. Membership changes.
3. **Ice, Life or Void gains placement that contradicts the table above.** The relevant modelling
   choice is re-opened — one row, not the roster.
4. **`DamageCause` gains or loses a member.** The corroborating surface moved; re-read it.

None are calendar-based, and none require anybody to remember: each is a thing a release does.

## What this does not claim

- It does not adopt terrain derivation, retire the convergence star, or change any mechanism.
- It asserts nothing about Hytale's intended taxonomy. It records what our content keys to and why,
  with the anchors and falsifiers for every piece we invented.
- It does not change code. If it ever appears to, something has added an enum — see above.
