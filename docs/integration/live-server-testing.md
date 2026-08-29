# Live Server Testing — operating notes

> **Verified against:** Hytale Server `0.6.1`. **Written:** 2026-08-29.
>
> **This document expires.** Like [`hytale-capability-audit.md`](hytale-capability-audit.md) it
> describes one build of a game in Early Access that updates every 2–6 weeks. Every command form,
> asset id and method signature below is a *hypothesis* after the next launcher update. Re-verify by
> executing, not by reading — see [Probe discipline](#probe-discipline).
>
> To confirm which build you are on:
> `unzip -p <HytaleServer.jar> META-INF/MANIFEST.MF | grep Implementation-Version`

---

## Why this document exists

These facts were carried as **"Standing cautions"** in the vault's `Hytale_Session_Status.md` —
about thirty bullets across nine per-session sections, chained together by a closing line of the
form *"all session-7 through session-11 cautions still stand."* Assembling the real list meant
reading six sections spread over 750 lines of an 1857-line document, at session start, hours before
any of it applied.

That filing has a measured failure rate. The cwd rule was written on 2026-08-28, read at session
start on 2026-08-29, and violated three times that day. The predictor is frequency, not importance:
`git checkout main` before `gh pr merge` is 8 for 8 as pure prose because it fires at one rare,
deliberate moment, while a rule that fires on every keystroke gets skipped exactly when judgement is
cheapest to skip.

So the cautions were split by **half-life**. This file is the perishable, engine-specific half — the
part that expires when the game ships. It is the committed home for what was previously spread
across the session doc and `.scratch/hytale-server/README.md`, which is **git-ignored** and
therefore was never durable.

The permanent, method-shaped half stays in the session doc's canonical cautions section and in
`Hytale_Observations.md`.

---

## The test server harness

**One server directory, reused:** `.scratch/hytale-server/`. Per-session directories
(`native-objectives-spike/`, `act-ii-verify/`) had accumulated to **432M** by 2026-08-25, almost all
of it generated `universe/` chunk data that regenerates free on the next boot. Both were deleted;
the harness is now ~24K. **Reuse this directory rather than making a new one per session** — that is
what accumulated.

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
./gradlew :mod:hytale:shadowJar
cp mod/hytale/build/libs/*-all.jar .scratch/hytale-server/mods/
bash .scratch/hytale-server/run-server.sh
# connect the client via Direct Connect to 127.0.0.1:5520
```

`auth.enc` and `permissions.json` are kept deliberately — they carry cached server credentials and
`hytale:Admin`, so **no `/auth login device` and no `/op` is needed**. A fresh server directory
starts unprivileged, and the resulting permission refusal **reads exactly like a syntax error**.

### Boot flags that do not do what they look like

- **Never `--auth-mode offline`.** It is a valid value of the flag's enum. The server accepts it,
  boots clean, binds the port — then refuses every client at login with *"offline mode is only valid
  in singleplayer"*. The failure is at login, not at boot, so everything looks healthy until a client
  tries to connect.
- **`--bare` and `--validate-assets` cannot check a mod's assets.** Under `--bare` the
  `PluginManager` never runs, so no mod loads. `--validate-assets --shutdown-after-validate`
  completes before mods load. Both then fail on `failed to validate instances`, which is unrelated
  and reads like a real failure. **Only a normal boot registers a mod's pack.**

### Cleaning the harness

Measure with **`du -sh .`, never `du -sh */`.** A glob cannot see dotfiles: **63M** hid in
`.scratch/hytale-server/.cache/prefabs/` through three cleanups that each reported success.

Delete by what is *not* on the keep-list: `auth.enc`, `config.json`, `permissions.json`,
`README.md`, `reference/`, `run-server.sh`.

---

## Console control

The server console needs a **real TTY**. Launched with plain redirected stdin, JLine falls back to a
`dumb` terminal and **silently ignores every typed command** — no error, no echo. Wrap it:

```bash
script -qfc 'bash .scratch/hytale-server/run-server.sh' /dev/null < fifo
```

**The FIFO holder must not expire.** `( sleep 3600 > fifo )` died mid-session; the console silently
stopped accepting commands, `stop` never arrived, and the failure presented as a hung server. Use a
holder that cannot time out:

```bash
tail -f /dev/null > fifo
```

**Check liveness by port, not by process name.** `pgrep -f` / `pkill -f` match the shell running
them — that killed one of our own commands and, separately, reported an already-dead server as
"STILL ALIVE".

```bash
ss -ltn | grep 5520
```

---

## In-game commands

Required arguments are **positional**; optional arguments are **`--name=value`**:

```
/give <player> <item> --quantity=1
/objective reachLocationMarker add <id>      (places it at your position)
/objective start objectiveLine <id>
/objective history
/gamemode <adventure|creative|a|c>
```

**The chat box autocompletes as you type — that is a better authority than reading argument names
out of the jar.**

- **`/time set <hour>`** — or `dawn|midday|dusk|midnight`. **`/time set hour <n>` is not a command:**
  the wrong form parses, does nothing, and reports nothing. It cost a full sweep before `time` was
  read back to check.
- **`/objective locationmarker` has `enable`/`disable`, world-scoped.** A disabled marker fails
  silently and reads exactly like a broken asset.
- **`/objective complete objective <id>` reaches a state without exercising it** — it does not roll
  up to line-level bookkeeping.
- **`/weather set|get|reset` is console-runnable** (`AbstractWorldCommand`), so weather tests need no
  client. Placing markers and reading `/stormseeker` are player commands and do need one.

---

## Weather-gated content

**Never test weather-gated content with `/weather set`.** It freezes `environmentWeather`; the gate
then fails silently and reads exactly like a broken asset. Instead:

- use **natural** weather;
- **`/time set <hour>`** re-rolls every environment's forecast;
- **`time pause`** freezes a good state before placing markers.

**`weather reset` is not "clear".** It returns to *natural* weather, which can be a storm. A negative
control must force an explicit calm id (`Zone1_Sunny`).

---

## Assets and prefabs

- **Resolve assets by id, never by index.** `Env_Zone1_Plains` moved 65 → 64 → 65 across three
  restarts of the *same build*. A whole hour-sweep ran against the wrong environment before this was
  caught.
- **Prefab names are the path under `Server/Prefabs/` *with* the `.prefab.json` suffix.**
- **Use the 8-arg paste: `paste(…, 1, 4, store)`.** The 6-arg overload writes only into **resident
  chunks** and fails silently — eight successive zero-changed-block probe results were all the 6-arg
  call. With the 8-arg form, placement works **anywhere: no player, chunks unloaded**.
- **`setBlock` is a raw write with no support check.** There is no `placeBlock` in 0.6.1.

  > The capability audit's row describing `world.accessor.BlockAccessor` with `placeBlock`,
  > `testPlaceBlock` and eight `setBlock` overloads is **wrong for 0.6.1** — that class does not
  > exist. The real API is `IChunkAccessorSync`, with two `setBlock` overloads and neither of the
  > other two methods.

- **`ParticleUtil.spawnParticleEffect` is public static and takes a raw position.** A plugin can emit
  any shipped particle anywhere with no NPC; `ActionSpawnParticles` is merely one caller of it.
- **`ChunkPreLoadProcessEvent` fires on every chunk load**, so anything it writes must be idempotent
  via a world-read sentinel.
- **Crystal rock name ≠ shard name.** `Rock_Crystal_Water` drops Blue; `Rock_Crystal_Blue` drops
  Cyan. Read the `Gathering.Breaking.DropList`, do not infer from the family name.

---

## Probe discipline

- **Never place probe markers in a line.** The first `/residueprobe` layout put three markers east
  and two were hidden by terrain and by each other. **A marker you cannot see is indistinguishable
  from one that never rendered.** Use distinct bearings and multiple heights.
- **Place the case you predict will be ABSENT first, while nothing else is on screen.** A marker
  missing among four others is ambiguous; one missing when it is the only marker in the world is not.
- **Signature inspection is a hypothesis; a probe executed on the current build is confirmation.**
  The capability audit was taken by `javap` against 0.5.9 and is materially wrong *per row* — two
  spot-checked rows are still accurate, which is worse than uniform rot, because it reads as
  trustworthy.
- **When a probe returns nothing, change one variable and re-run before explaining why.** Of seven
  iterations of the prefab-paste probe, five were faults in the *instrument* rather than findings:
  wrong metric, wrong location twice, a patch that deleted the method's only `sendText`, and a scan
  box smaller than the prefab.

---

## Inspecting the jar and assets

- **`grep` needs `-a` on class files.** Without it, matches in binary content are suppressed.
- **`unzip 'dir/*'` matches only one level deep.**

Both produced confident **false negatives** about the game's contents before being caught. Treat an
empty result from either as unproven, not as absence.

Regenerate asset vocabularies with `--generate-asset-schema` rather than trusting transcribed lists.
