# API baseline

A diffable snapshot of the Hytale server jar's API surface, captured once per game update.

## Why this exists

On 2026-10-20 the launcher updated to 0.7.2, crossing three releases in one jump, and **overwrote
the only copy of the previous jar on disk.** The session that followed could establish that the
build was red, which seven symbols had gone, and what our code used — but it could not establish
*what the engine had changed*, because there was nothing to diff against. Every "new in Update 7"
statement in `docs/superpowers/specs/2026-10-20-hytale-0.7.2-capability-assessment-design.md` rests
on patch notes rather than on measurement, and the notes were already wrong once in that session.

This turns the next update into a diff.

## Using it

```bash
bash scripts/api-baseline/capture.sh          # after a launcher update
diff -ru snapshots/<old> snapshots/<new>      # what actually moved
```

Read the **`BASELINE_VERDICT: CAPTURED|FAILED`** line by name, not the exit code — this workspace
has recorded five occasions where something appended output after a verdict and the reader saw the
wrapper instead.

Capture is **deterministic**: re-running against the same jar is byte-identical, verified
2026-10-20. That is the property that makes a diff trustworthy, so re-check it if the script changes.

## What a snapshot contains

| File | Contents |
|---|---|
| `identity.txt` | Manifest `Implementation-*` lines, plus the jar's SHA-256 and byte size. `Implementation-Version` and `Implementation-Revision-Id` name the build exactly; the snapshot directory is `<version>-<rev8>` |
| `classes.txt` | Every **top-level** `com.hypixel.*` type, sorted. 6,895 at 0.7.2. Catches additions, removals and package moves — the `Vector3d` move that went unnoticed for ~6 months would appear here as one line gone and one added |
| `signatures.txt` | Full `javap` output for **exactly the types this project imports**, derived from `mod/hytale` sources on every run so it tracks our real dependency surface instead of a hand-maintained list that would drift. 50 types at 0.7.2 |

## What it does NOT cover — read this before trusting a clean diff

- **Inner classes are excluded from `classes.txt`.** Inner-class churn is noise at this volume, so a
  change confined to one is invisible there. `signatures.txt` covers it for types we actually use,
  and nothing covers it for types we do not.
- **Only `mod/hytale`'s imports get signatures.** That is the whole engine-facing surface today,
  because `core` and `quests/stormseeker` contain zero `com.hypixel` references by design and the
  build enforces it. If that ever changes, widen the grep in `capture.sh`.
- **Behaviour is not covered, only shape.** The defect that cost the most on 2026-10-20 was
  `SectionReader.getBlock` returning 0 for an unloaded section where `World.getBlock` had loaded on
  demand. Both are `int getBlock(int,int,int)`. **A signature diff cannot see that**, and no
  extension of this tool would — it is what the live probes under `mod/hytale/.../command/` are for.
- **Assets are not covered.** `Assets.zip` is ~3.9 GB and holds the asset schemas; a block id shifted
  between 0.6.1 and 0.7.2 (`2175` → `2356` at the same world position) with no API change at all.
- **A `javap` signature is not an availability guarantee.** 0.7.2 marked `BlockChunk`/`WorldChunk`
  accessors `@RestrictedApi`, which is an ErrorProne annotation absent from bytecode — so a
  restricted method looks identical to a supported one here.

## Retention

Keep every snapshot. They are ~520 KB of sorted text each, which packs small, and the whole point is
being able to diff across more than one update. Do not prune them to save space — the 2026-10-20
session is what pruning looks like.
