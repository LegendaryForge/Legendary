# Stormseeker — narrative redesign

**Status:** design agreed 2026-08-25. Not yet executed. This spec is what
`docs/stormseeker/stormseeker-canonical.md` v4.0 gets rewritten from.

**Supersedes** the story in `docs/stormseeker/archive/narrative.md` (v1.2, 2026-02-04) and the
phase structure in `stormseeker-canonical.md` v3.1. Absorbs the decided parts of
`docs/stormseeker/canon-alignment-recommendations.md`, which retires once this lands.

---

## Why this exists

Three problems, found by reading the existing documentation against the game as it ships today.

**1. Two documents described two different questlines.** `archive/narrative.md` runs Storm Unease →
Storm Attunement → Aftershock → Trials → Ancient Forge → **Leyline Revelation** → Convergence →
Epilogue. `stormseeker-canonical.md` runs The Watching Elemental → The Trek → Attunement → Trials →
Craft Frame → Final Encounter → Epilogue. These are not renumberings of one story: the archived
version makes the revelation that the world has hidden geometry a **third-act payoff**, and the
canonical version makes it a **first-act reward**. Both claimed authority; neither was reconciled.

**2. Narrative coverage was inverted.** Only 4 of 7 phases carried a `### Narrative` section —
Phases 4, 5, 6 and the Epilogue. **The entire opening act had none**, described purely
mechanically. The Epilogue, which almost nobody reaches, was the most fully realised writing in the
document.

**3. Nothing delivered the story.** A grep for any text-delivery mechanism across the Stormseeker
docs — dialogue, books, inscriptions, journals, tooltips, title or description keys — returned
nothing. The prose in `archive/narrative.md` was **unreachable by the player**. The Core Principle
*"No quest UI required. The world teaches the player through feel, not markers"* is right in
spirit and had been applied as a prohibition on all text, which is stricter than the tradition it
imitates. Hytale's own "archaeology" method is not text-free — it is **text placed in objects
instead of quest logs**, and `docs/setting/hytale-orbis-setting-brief.md` §2 already identified item
flavour text as "the model to imitate."

The vocabulary was also invented rather than canon-native: `leyline`, `resonator`, `sigil` and
`attune` have **zero occurrences in `server.lang`**, while the game ships `Spirit_Thunder`, a storm
material chain, and an Elemental Circle family with a hole in it.

---

## 1. The premise

> **The storm has no Circle because the people who built one used it to open a door, and the door
> cost them everything — including the thing that used to answer.**

This reconciles three pieces of shipped canon that currently sit unconnected:

- *"The true source of such magic is nowhere to be found"* (`Ingredient_Lightning_Essence`) — it is
  not hidden. It stopped answering.
- *"The elementals born from its lingering traces still wander the lands"* — they wander because
  there is no Circle to gather at. They are not free spirits; they are **unhoused**.
- **Storm alone has an essence and a spirit and no Elemental Circle** — Earth, Fire, Frost, Poison
  and Sand all have one. Not an oversight in the world: a wound in it.

It also puts Orbis's stated central theme — advanced civilisations, hubris, self-caused downfall —
at the centre rather than in the set dressing.

**Player arc:** get noticed by the residue → learn what was lost → raise a new Circle → make the
storm answer to it → stand where the builders stood, in front of the same door.

**Thematic tension:** the player is doing exactly what the builders did — same site, same
materials, same ambition. The final question is not "can you win" but "do you understand why they
lost." Stormseeker is proof you were **answered**, not proof you conquered. February's best line
survives and becomes literally true: *the storm is not an enemy, it is a gate.* There is now a gate.

**Windrider Valley** is what lies behind it. The game ships a portal key for *"Fragment: Orbis —
Windrider Valley"*, a place named and unreachable. The fallen civilisation is who reached it.

**What the player ends with:** a monument they raised, permanently in the world, completing a family
the game shipped incomplete. Other players walk past it.

---

## 2. Design principle — No neutral verbs

> **Every action the player repeats should ask a question the story cares about. If a verb asks
> nothing, it is a chore — condition it on the world, or cut it.**
>
> **But not every verb at full intensity.** Ask a real question, then give the player somewhere to
> put their hands down.

The test for any mechanic: *what question does this ask, and what does the answer teach about the
world?* No question means the player is performing labour so a number goes up.

This is the **mechanism** behind "the world teaches the player through feel, not markers" — a goal
that previously had no method attached. Conditioning verbs on world state is the method.

| Verb | Normally | Here | The question |
|---|---|---|---|
| Gathering | Click rocks | Storm-timed, perishable, cultivable | *How much will you take?* |
| Travel | Follow the marker | No markers; read residue, terrain, sky | *Can you read where you are?* |
| Waiting | Watch a cooldown | Storms are rare; prepare, observe, position | *Are you ready when it comes?* |
| Exploring | Reveal the map | Crystal density **is** the map | *Can you find what isn't marked?* |
| Building | Place blocks | Raise the Circle where you chose, permanently | *Where will you commit?* |
| Fighting | Kill for drops | The unhoused have nowhere to go | *Will you kill what can't leave?* |
| Crafting | Open menu, click | At your Circle, in a storm, at the gate | *Have you earned the conditions?* |

**The counterweight is load-bearing.** A principle like this fails by succeeding too hard; if every
verb is fraught the mod is exhausting. Some verbs must be **restful on purpose** — tending thistle
at your Circle is the designated one. It earns its place by *not* asking a hard question, and it
makes the peak-in-a-thunderstorm moments land harder by contrast.

This principle is reusable and should govern questline #2 as much as this one.

---

## 3. Act structure

Five acts plus an epilogue, paced as **hook → mystery → competence → labour → payoff**. Replaces the
six-phase structure entirely. Each act has a job, a feeling, a visual, and a delivery channel.

### Act I — The Mark
*One storm. Minutes. **Zero words.***

A thunderstorm behaves wrongly. Something watches from the edge of it — an elemental that does not
approach and does not attack. Get close and it leaves, fast, scorching the ground as it goes. The
trail persists until the storm ends.

No text, no marker, no NPC, no objective entry. **The first act contains no words at all**, which is
what makes the first words in Act II land. The player does not know there is a questline. They know
they were noticed.

Mined from February, which had this instinct and was right about it.

### Act II — The Trace
*A session. The first words in the story.*

The trail leads to a ruin: a wind temple nobody finished — the **first use of the `Temple_Wind`
palette anywhere in the game**. At its centre, the Statue of a Silent Deity.

The story speaks for the first time, and only through **inscriptions**: the builders' own record,
not a quest-giver. What it tells you is that there was going to be a Circle here, and something
happened.

### Act III — The Listening
*Several sessions. The world gets re-read.*

You cannot rebuild what you do not understand. This act teaches reading where residue gathers, and
its payoff is that **the storm materials the player has been carrying all along suddenly mean
something** — Stormsilk, Storm Hide, Essence of Lightning and Storm Thistle all ship ungated today,
so most players will have handled them before meeting this questline.

`Spirit_Thunder` matters here: it is the **only source of Essence of Lightning in the game**. Which
poses the act's real question — the builders *harvested* the unhoused, and you need another way to
be given what they took.

### Act IV — The Raising
*The long middle. The signature visual.*

Find a site the sky can see — high, exposed, storm-prone — far enough from any other Circle. Raise
it **tier by tier**, using the 01→07 convention the game already ships for Earth and Frost.

Some tiers may only be raised during a storm. This turns storm rarity from a problem into the
pacing governor: there is work to do between storms, and storms become **anticipated events**
rather than weather. Not every tier should require one, or it becomes a wait.

This is where accomplishment accrues — visibly, permanently, in public.

### Act V — The Answer
*The climax. Three beats.*

The Circle stands and stays **silent**. Something has to call it.

1. **The proving.** Whatever the trials become, their dramatic job is fixed: demonstrate you can be
   *answered* without being *consumed* — the exact thing the builders failed at.
2. **The answer.** The storm responds. Elementals gather at the beacon for the first time in the
   world's history. The Circle joins the family.
3. **The door.** An answered Circle can finally read what the builders did — the way they opened,
   still there. Stormseeker is forged in that moment, at the gate, in the storm. The forging **is**
   the confrontation.

### Epilogue
The Circle remains. Other players walk past it. The sky knows your name.

### Consequence

The **skip path is gone**. February allowed standing on a plate during a storm to skip two phases.
Nothing here is skippable, because the Circle has to be built.

---

## 4. Materials

February's material taxonomy was the strongest design in the old document and mostly survives. The
one-line model, updated:

> Generic materials build legendaries. Storm materials define Stormseeker. Crystals reveal space.
> Keystones become the monument.

Two hard rules carry over unchanged, and both matter: **storms gate access, not progress state**,
and **no quest-state-dependent world spawning**. The world is the same world whether or not you are
on the questline, which is what makes finding it feel like discovery rather than like a quest
switching on.

### Class A — Generic Legendary Materials *(unchanged)*
Shared backbone across Legendary lines; no storm, leyline or questline dependency. More justified
now than when written, given the per-element family direction. Scope reference updates from
`LegendaryCore` (a repository that no longer exists) to `:core`.

### Class B — Skyglass *(was "storm-charged ore")*
Fulgurite. Lightning striking exposed stone fuses it to glass. It exists **because** the storm
happened, on peaks and ridges, and it does not keep — harvest it in the storm that made it or lose
it. Explains itself without a word of lore.

*Question it asks: how much risk will you take?* Gathering it means standing on an exposed peak in a
thunderstorm while lightning is still striking. `DamageCause` is an authorable asset type (15 ship
today), so a real Lightning damage cause is available to a pack, with `CameraShake`, particles and
sound behind it.

### Class C — Residue Crystal *(was "leyline-bound crystal")*
Concept survives; the word "leyline" does not. Crystals form where residue gathers, density scaling
with proximity to a current.

Its stated purpose — *"crystals reveal space"* — becomes literal: **crystal density is how you find
a Circle site.** The material is the map. Act IV's siting mechanic and Class C's purpose collapse
into one thing, and the player learns to read the land instead of being told where to build.

**Harvesting model — cultivation, not depletion.** An earlier draft made stripping a site thin the
current permanently. Rejected: it punishes the attentive player and is invisible to the inattentive
one, and on a shared server it would block questlines. Instead:

- Anyone can strip a site bare. No punishment, no frustration, and they get a real haul.
- A player who harvests attentively — mature clusters, core left intact — can carry a **residue
  core** back to their Circle and cultivate crystals there.
- Wild sites are the **discovery** source. Cultivated crystals are the **sustained** source.

Carelessness gets one payday; care gets a renewable supply at home. A stripped shared site becomes a
minor annoyance rather than a blocked questline.

This is also better lore than depletion was. The unhoused wander because there is nowhere to gather,
so residue **collecting at your Circle is proof the Circle is working** — a progress signal with no
UI, no number and no text. It merges with thistle cultivation to make the Circle the place you
keep, which is the restful counterweight the design principle requires.

### Class D — Keystones *(was "sigils")*
The rules are right — deterministic, state truth not loot, never NPC-decided, never RNG — but the
item was an inventory abstraction. Each proving grants a Keystone, and each Keystone **is a tier of
the Circle**. The token does not represent progress; it becomes the monument.

### Class E — Essence of Thunder *(new; required by the premise)*
The builders **took** and the player must be **given**, so the design needs a material that cannot
be harvested.

Lightning is the strike; thunder is the **answer**. Essence of Thunder cannot be mined, killed for,
or crafted. It is given, once, when the Circle answers — by a `Spirit_Thunder` that came because
there was finally somewhere to gather.

The two essences carry the theme with no exposition: **one is taken, one is given, and both go in
the sword.**

### Stormseeker is forged from

| Class | Material | How it is obtained |
|---|---|---|
| A | Generic legendary backbone | Ordinary rare world materials |
| B | **Skyglass** | Peaks, during storms, perishable |
| C | **Residue Crystal** | Where currents settle — also how the site was found |
| — | **Essence of Lightning** | Taken. The shipped material; the builders' way |
| E | **Essence of Thunder** | Given. Only at an answered Circle |

Forged at the player's own Circle, in a storm, with both essences.

**Nothing here duplicates the shipped chain.** Storm Hide, Stormsilk and Storm Thistle remain the
mundane harvest anyone can do. Ours sit above them, and the fact that ordinary players already
handle storm materials is what makes the escalation legible.

Names are first-pass and deliberately plain-compound, matching Hytale's own convention
(`Storm_Hide`, `Storm_Thistle`, `Ingredient_Bolt_Stormsilk`).

---

## 5. Lore delivery

Four channels, each with a distinct job. All four were chosen deliberately over the previous
"atmosphere only" model, which delivered nothing.

### Governing rule — never override shipped item text

Overriding base-game item descriptions is technically possible (proven 2026-08-25, see
`docs/integration/hytale-asset-packs.md`) and is **prohibited here**. It changes the game for every
player on a server including those not on the questline, and in this case it would destroy the
effect, because the shipped text is already load-bearing:

> *"Raging storms have proved attractive to many creatures over the centuries. Though the true
> source of such magic is nowhere to be found, the elementals born from its lingering traces still
> wander the lands."* — `Ingredient_Lightning_Essence`

That is **already Act III's revelation.** Every player who has picked one up has been carrying the
answer unread. The story teaches them what it meant. **Recontextualise, never rewrite.** New text
goes only on our own items.

### Structure inscriptions — where the builders speak
The strongest channel and the one nothing else can do: fragmentary, in the ruin, in their own voice.
Register, by example:

> *"We raised five. The sixth would not stand."*

Literally true in the shipped game — Earth, Fire, Frost, Poison, Sand.

> *"The others gather where we set the stones. This one would not be housed."*
>
> *"It answered once. We asked again."*
>
> *"We carved it listening. It has not spoken since."* — at the statue

### Objective text — in-fiction, never instructional
Authored regardless, so the only cost is writing them well. The questline line is **"The Sixth
Circle."**

| Instead of | Write |
|---|---|
| Reach the location | *Follow what the storm burned* |
| Gather 3 Storm Thistle | *Take only what the storm has already given* |
| Craft the frame | *Shape something worth answering* |

### Item flavour text — our items only
> *"Shaped for a voice that has not come."* — the incomplete frame
>
> *"The sky does not obey it. The sky answers it."* — Stormseeker

### NPC dialogue — expression, never entitlement
An NPC may **react** to what the player has done and may never gate, grant, or direct it. Use it in
exactly one place: after the Circle answers, someone who has seen the sky do something it has not
done in living memory. One reaction, late. If it starts explaining the questline, it has failed.

### The rule underneath all four
**Every channel opens later than expected.** Act I has no words; Act II is the first text. The world
speaks only after it has the player's attention. That inversion is what the old "no quest UI"
principle was reaching for and could not achieve, having no channels at all.

---

## 6. Multiplayer and siting

**The player raises their own Circle.** There is no Storm Circle in the world — that is canon — so
the player does not claim a scarce landmark, they build one at a site that qualifies on **terrain**
(high, exposed, storm-prone), subject to a minimum spacing rule from other Circles.

Rejected alternatives and why:

- **Instanced Circle** — Hytale ships a first-class instance system (`Server/Instances/` includes
  `Dungeons`, `Portals`, `Persistent`, `ShortLived`, `Regions`), so this was available. Rejected
  because nobody else would ever see the monument, which discards the entire payoff.
- **One shared Circle per server** — no contention and a strong one-time event, but every player
  after the first gets a materially lesser questline.
- **Rare fixed sites** — better art control, but reintroduces contention on large servers.

Terrain-qualified siting **scales the right way**: a bigger world has more sites automatically, and
on a large server storm circles appear across the landscape over months, which reads as the element
returning rather than as a queue. The failure mode of scale becomes a feature.

---

## 7. Deliberately open

Not omissions — decisions that should be made later, by someone with information we do not have
yet.

- **The trials' mechanics.** Act V beat 1 fixes their *dramatic* job and nothing else. The current
  implementations are scaffolding (`AnchoredTrialSession` is `REQUIRED_STATIONARY_TICKS = 40`;
  `FlowingTrialEvaluator` scores self-coherent movement with no storm gradient), and are under
  active development. This spec does not constrain them beyond their narrative function.
- **Zone anchoring.** Thunder weather exists **only in Zone 2** of the 87 shipped weather
  definitions. That would anchor the ruin and probably the Circle to Zone 2. Real constraint, worth
  deciding deliberately rather than inheriting.
- **Tier count for the Storm Circle.** Earth ships 7 tiers, Frost 5. Storm's count is a pacing
  decision tied to Act IV's length.
- **Minimum spacing** between player Circles.
- **Class A materials** — the shared legendary backbone is named but not specified, and should be
  designed with the elemental family in mind rather than for Stormseeker alone.
- **Where the single NPC reaction lands**, and who it is.

---

## 8. Documentation changes this spec authorises

1. **Rewrite `docs/stormseeker/stormseeker-canonical.md` to v4.0** — new premise, five acts,
   material classes, delivery model, and `No neutral verbs` added to Core Principles. Narrative
   stays **inline per act**; a separate story document is what produced two divergent questlines
   last time.
2. **Demote the Thunderfury parallel table** from a mapping to a lineage note. Its material rows are
   now wrong, and as a spec it steered the design toward "Thunderfury, but Hytale."
3. **Archive `docs/stormseeker/canon-alignment-recommendations.md`** once absorbed. It was
   explicitly "recommends, not decides"; this spec decides.
4. **Amend `docs/setting/hytale-orbis-setting-brief.md`** §6 to record that `Temple_Wind` is a
   complete but **unplaced** palette and the Wind `Statue_Gaia` appears nowhere in the world — the
   fact that makes them available to us without diluting anything.
5. **Leave `archive/narrative.md` archived**, with a note recording what was mined from it: the
   covenant framing, *"the storm is a gate"*, and the Epilogue's register.

Execute in that order, as separate changes, so the canonical rewrite is reviewable against a spec
that already landed.
