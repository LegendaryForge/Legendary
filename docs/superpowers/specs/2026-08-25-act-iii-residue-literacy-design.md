# Act III — The Listening: residue literacy as a reading of *place*

**Status: decisions captured, design incomplete.** This records what was settled in the
2026-08-25 brainstorm and the measurements behind it, so none of it depends on anyone
remembering. Open questions are marked as open; §7 lists what still has to be designed
before this can become an implementation plan.

Supersedes the Act III guidance in `Hytale_Session_Status.md` session 8, and narrows
`stormseeker-canonical.md` §Act III without contradicting it.

---

## 1. The two findings that reshaped this

Both are from 2026-08-25 and neither was known when Act III was written.

**Weather is a property of place, not of time.** Storm chance is a static field on each
`Environment` asset. It varies by more than an order of magnitude between environments and is
essentially flat across the 24 hours — 6 of the 9 storm-capable environments have identical odds
in every hour. The top-to-bottom ratio was **32×** when first measured against 0.6.0 and is
**~46×** on 0.6.1; that figure is the least stable thing in this section, because it is a ratio
of the highest rate to the *lowest*, and the lowest are the three Zone1 environments whose
weights the patch re-tuned. The robust form of the claim is the one that did not move: two
environments sit at 50%/hr and the Zone1 family sits near 1–2%/hr.

**A forecast is not derivable.** The engine's weather roll is seeded with
`dateTime.hashCode()` of whichever server tick first observes the hour change. That tick
lands after `hh:00:00.000` with real-time scheduling jitter, and `LocalDateTime.hashCode()`
includes nanoseconds. Nothing outside the engine can reconstruct it. See §5.

Together these say: the axis worth reading is **where**, and it is readable exactly, with no
prediction involved.

---

## 2. Measurements

Storm chance per game-hour, from the shipped `Environment` assets. 50 of 122 environment
files declare `WeatherForecasts` directly; the other 72 inherit from a parent, so these
values propagate rather than being exceptions. "Storm" means a weather asset tagged
`Storm` — `Zone1_Storm`, `Zone2_Sand_Storm`, `Zone2_Thunder_Storm`, `Zone3_Snow_Storm`,
`Zone4_AshWastes_Storm`, `Zone4_Storm`, `Zone4_Swamp_Storm`.

| Environment | storm chance / game-hour |
|---|---|
| `Env_Zone3_Glacial_Henges` | **50.00%** |
| `Env_Zone4_Crucible` | 50.00% |
| `Env_Zone3_Glacial` | 15.00% |
| `Env_Zone2` | 5.26% |
| `Zone2_Overground`, `Env_Zone2_Deserts` | 2.50% |
| `Env_Zone1_Plains` | 1.87% |
| `Env_Zone1` | 1.52% |
| `Zone1_Overground` | 1.08% |
| every cave, dungeon and interior | 0% |

> **Re-measured against 0.6.1 on 2026-08-27.** Everything at or above 2.50% is byte-identical to
> the 0.6.0 reading, as is the structure: 122 environment files, 50 declaring / 72 inheriting, the
> same seven `Storm`-tagged weathers, 9 storm-capable, 6 of 9 flat across all 24 hours. Only the
> three Zone1 rows moved, and not because storms were re-tuned — the storm **weights** are
> unchanged. Their forecast tables were re-weighted elsewhere, so the denominator shrank
> (`Env_Zone1_Plains`: 2/128 → 2/107) and the storm's share rose. The three Zone1 rows are also
> the three non-flat environments, so a single number understates them; the values above are the
> **maximum** hourly rate, and `Env_Zone1_Plains` reaches 0.00% in its quietest hours.
> Superseded 0.6.0 values, for provenance: 1.56%, 1.41%, 1.04% — headline ratio 32×.

**Game time runs 30× real time** — measured, 30 real seconds produced exactly 15 game-minutes.
So **one game hour = 2 real minutes**, and a full day = 48 real minutes. **Re-confirmed on 0.6.1**
(2026-08-27): 60.01 real seconds produced 30.01 game-minutes, ratio 30.01×. Worth re-checking
because 0.6.1 shipped a "moon phase/time-dilation no longer incorrectly reset" fix; it did not
change the rate, so every conversion below stands.

Converting the table:

| Standing in | mean wait for a storm |
|---|---|
| `Env_Zone3_Glacial_Henges` | **~4 real minutes** |
| `Env_Zone1_Plains` | **~2 real hours** |

That ratio is the design.

---

## 3. Decisions

**D1 — Nothing is granted.** No sight toggle, no unlocked ability, no UI. Confirmed against
the v4.0 rewrite (`7f7bcf9`), which deleted v3.1's "leyline sight" deliberately. Act III makes
the *player* more perceptive, not the character.

**D2 — Core protection gates the action, not the perception.** Canon says both "nothing is
granted" and "cores are visible only from Act III onward"; those cannot both be literally
true. Resolution: anyone can see a crystal cluster; only a player who has read the builders'
record can **take the core**. Same outcome — a casual player cannot destroy a shared site —
with no visibility gating anywhere.

**D3 — Residue literacy is a reading of place.** It answers *"where do storms happen"*, not
*"when will one come"*. This is what makes it dependable: it is a static asset property, so
it cannot mislead the player.

**D4 — Residue density is derived from storm frequency, not authored.** Residue is deposited
by storms, so density **is** the historical record of storm frequency. Deriving it from the
environment's own storm weight makes canon's *"the material is the map"* literal, costs no
content authoring, and stays correct by construction if Hypixel retunes the tables.

**D5 — The questline is a journey out of Zone 1, but Zone 1 is not empty.** Residue currents
run everywhere; storms occur in Zone 1 too. What is cross-zone is the *gradient* — inside
Zone 1 everything sits between 1.04% and 1.87%, so the signal is present but nearly flat,
while the 50% sites are in Zone 3 and Zone 4. Following the residue *upward* means leaving the
starting zone. A player who stays can still obtain everything; it simply takes longer. The base
game does not stop players exploring dangerous zones and neither will this questline.

**D6 — Danger is the pacing mechanism.** The fast path is gated by **capability**, not by
timers, cooldowns or grinding: a fresh character cannot survive where the storms are. A
geared player earns Stormseeker faster, which is the correct outcome. Nothing here needs a
time gate authored on top.

**D7 — Circles may be sited anywhere, and site choice sets the player's own pacing.** Storm
frequency at the chosen site governs how long storm-gated Act IV tiers take: ~4 minutes each
at Glacial Henges, ~2 hours each in Zone 1 plains. **This is accepted, not patched.** It reads
as a real trade — build where it is safe and wait, or build where the sky is angry and be
hammered while you work — and site choice is already permanent in canon. Normalising it would
need per-zone tier tuning, which is the fiddly authoring that becomes grind.

**D8 — Yield scales with danger.** Skyglass and residue crystals are more plentiful in the
high-storm zones. A player unwilling to go there can still obtain them by waiting longer
elsewhere; the gradient is a preference, never a wall.

**D9 — Stormseeker does not own the world's weather.** Forcing weather was considered as a way
to author a schedule we could telegraph. Rejected: it is world-wide, it overrides natural
weather for every player, and it **breaks every base-game `WeatherTriggerCondition`** (see
`hytale-asset-packs.md` §8d). We do not need it, because we do not need to know the schedule.

---

## 4. What this buys, per consumer

| Consumer | Reads | Gets |
|---|---|---|
| Act III literacy | residue density | *"storms live here"* — the readable signal |
| Class B Skyglass | residue density | where to hunt: ~4 min/storm instead of ~2 h |
| Class C crystals | residue density | where clusters form; *"crystal density is how the player finds a Circle site"* |
| Act IV site qualification | residue density | *"a site the sky can see — high, exposed, **storm-prone**"* |

One derived field serves all four. No prediction, no forcing, no persistence.

---

## 5. The forecast spike — run, failed, do not retry

Recorded so this is not attempted a second time.

**Hypothesis.** The natural roll is a pure function of public inputs, so a plugin could compute
next hour's weather:

```java
long h = HashUtil.hash(seed, env.getWeatherSeedKey().hashCode(), dateTime.hashCode());
WeatherForecast picked = env.getWeatherForecast(hour).get(new FastRandom(h));
```

Every accessor is public and the formula is correct — it is read straight from
`WeatherSystem$TickingSystem` bytecode.

**Method.** Reconstruct the *current* hour's roll two ways — datetime truncated to the hour,
and the exact current datetime — and score both against what the engine actually stored, for
all 123 environments.

**Result.** Never exact, across three trials at different hours:

| Set hour | `gameDateTime` at probe | truncated match | exact match |
|---|---|---|---|
| 9 | `09:00:29.825` | 84/123 | 96/123 |
| 14 | `14:00:29.916` | 114/123 | 97/123 |
| 19 | `19:00:29.792` | 85/123 | 97/123 |

The steady ~96–97 floor is the environments whose forecast table has a single dominant
outcome — caves are `Cave_Shallow` whatever seed you feed them. The reconstruction reproduces
nothing beyond those degenerate cases.

**Why it cannot work.** The seed uses the `LocalDateTime` of whichever tick first observes the
hour change, and that carries sub-second scheduling jitter which `hashCode()` includes.

**Cost of the failure: near zero**, because §1 showed the time axis carries almost no
information anyway. Even a working next-hour forecast would have bought **≤2 real minutes**
of warning.

> A caution for whoever reads this next: an earlier run of six identical repeats was taken as
> evidence that the roll was hour-granular. It was not — those were all degenerate-table
> environments agreeing with themselves. A differential test over inputs that cannot vary the
> output proves nothing.

---

## 6. Shape of the implementation

Sub-projects, in dependency order. Each gets its own spec and plan.

1. **Residue Field** — a pure query, `residueDensityAt(position) -> double`, derived from the
   environment's storm weight plus deterministic seed noise for local variation. No blocks
   placed, no persistence, no worldgen dependency. Belongs in `:core` if it can be made
   engine-agnostic, otherwise `:quests:stormseeker`.
2. **World Reading** — the in-world expression of (1): particles and `DynamicLight`, scaled by
   density. `mod/hytale` only.
3. **Residue Crystals** — Class C: clusters, finite harvest, slow regrowth, the one irreversible
   core, and the D2 action gate.
4. **Site Qualification** — Act IV's consumer: elevation, exposure, storm-proneness, minimum
   spacing.

**Worldgen is not available to any of these.** Deferral #18: amending the already-shipped world
is a V1-only capability via `WorldGenModifier`, on the generator that is being retired. A
derived virtual field sidesteps this entirely — which is why (1) is a query and not a
generator.

**"Thistle lean" is not buildable.** Canon names crystal luminance, thistle lean and elemental
drift as the pre-storm signs. Storm Thistle is a *shipped* block
(`Common/Blocks/Foliage/Flowers/Stamina_T1_Storm_Thistle/`), and §7c establishes that behaviour
cannot be attached to shipped blocks. Luminance and drift survive; thistle lean does not.

---

## 7. Open — must be settled before an implementation plan

- **O1 — What does the residue signal look like?** Particles, `DynamicLight`, sound, or a
  combination, and at what density thresholds. Shaders are unavailable, so this is the whole
  vocabulary.
- **O2 — Local variation.** D4 gives density from the environment, which is uniform within an
  environment. Does the field need seed noise so density varies *within* a zone, and is that
  noise the thing that marks a specific Circle site?
- **O3 — Class C harvesting numbers.** Cluster size, depletion rate, regrowth rate. **The one
  place a number could quietly become a grind.** Set late, against play data, not now.
- **O4 — Does Act III need a completion condition at all,** or is it purely a literacy the
  player acquires, with the only mechanical effect being D2's core gate?
- **O5 — How is the literacy taught?** Canon's stated delivery is item flavour text on our own
  items — but the materials it names (Storm Thistle, Stormsilk) are base-game and cannot carry
  our text.

---

## 8. Provenance

Brainstorm of 2026-08-25, following the weather-mechanism work in
`hytale-asset-packs.md` §8d. Measurements taken against Hytale `0.5.9` / `214c57c5` on a live
server; the storm-probability table was computed from the shipped `Environment` assets.
