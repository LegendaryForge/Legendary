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
bash scripts/api-baseline/capture.sh                          # after a launcher update
python3 scripts/api-baseline/compare.py <old-dir> <new-dir>   # what the ENGINE changed
```

Read the **`BASELINE_VERDICT: CAPTURED|FAILED`** and **`COMPARE_VERDICT: ENGINE_UNCHANGED|ENGINE_CHANGED|FAILED`**
lines by name, not the exit code — this workspace has recorded five occasions where something
appended output after a verdict and the reader saw the wrapper instead. `compare.py` exits **0 for
either engine verdict** and 2 only when it could not compare: an engine change is the finding, not a
failure, and making it exit non-zero is how a verdict gets trained out of a reader (the reasoning
`scripts/test-census.py` records for using a floor rather than strict equality).

> ### Do NOT read a cross-version diff with `diff -ru`
>
> This README recommended exactly that until 2026-11-07, and it is **misleading by construction.**
> `capture.sh` derives its type list from `mod/hytale` sources *on every run* — deliberately, so the
> snapshot tracks our real dependency surface — which means the list moves between captures with
> **our** imports, not the engine's.
>
> Measured: the `0.7.4` → `0.7.6` raw diff was **299 lines, every one an addition**, which reads as
> two releases of engine growth. It was not. All 8 added blocks were types *we* began importing in
> PRs #146–#149 after the `0.7.4` capture, and engine change restricted to the 65 types present in
> **both** snapshots was **zero**. The raw diff fails informative-looking, which is worse than
> failing loud.
>
> `compare.py` separates the two: an `ENGINE:` section restricted to the shared type set, and an
> `OURS, NOT THE ENGINE'S:` section for the list delta. It derives the type list from
> `signatures.txt`'s `===== ` headers rather than a sidecar, so it works on every snapshot already
> committed — no backfill, and no second place for the list to drift out of. Mutation-tested
> 2026-11-07 in both directions: a shared-type signature change, a class added, a class removed, a
> shared type going UNRESOLVED, and a newly-imported type that does not resolve each flip the
> verdict to `ENGINE_CHANGED`; a self-compare and the real `0.7.4`→`0.7.6` pair report
> `ENGINE_UNCHANGED`.
>
> One mutation initially looked like a comparer defect and was a defect in the *test* — it edited
> `IPointGenerator`, which is not an imported type and so appears nowhere in `signatures.txt`. The
> standing rule applied: when a probe returns nothing, change one variable before explaining why.

Capture is **deterministic**: re-running against the same jar is byte-identical, verified
2026-10-20. That is the property that makes a diff trustworthy, so re-check it if the script changes.

## What a snapshot contains

| File | Contents |
|---|---|
| `identity.txt` | Manifest `Implementation-*` lines, plus the jar's SHA-256 and byte size. `Implementation-Version` and `Implementation-Revision-Id` name the build exactly; the snapshot directory is `<version>-<rev8>` |
| `classes.txt` | Every **top-level** `com.hypixel.*` type, sorted. 6,895 at 0.7.2; 6,897 at 0.7.4 and unchanged at 0.7.6. Catches additions, removals and package moves — the `Vector3d` move that went unnoticed for ~6 months would appear here as one line gone and one added |
| `signatures.txt` | Full `javap` output for **exactly the types this project imports**, derived from `mod/hytale` sources on every run so it tracks our real dependency surface instead of a hand-maintained list that would drift. 50 types at 0.7.2, 65 at 0.7.4, 73 at 0.7.6. **This list is capture-time state, not engine state** — it is why a raw cross-version `diff` misleads and why `compare.py` exists |

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

## Snapshots on hand, and one gap

`0.7.2-d769dc3d`, `0.7.3-e1a1986a`, `0.7.4-c9581a28`, `0.7.6-c540fda1`.

**`0.7.5` was never captured** — the launcher went `0.7.4` → `0.7.6` between sessions (the jar was
rewritten 2026-11-07 09:37, minutes before the session that noticed), so the `0.7.4`→`0.7.6`
comparison spans two releases combined. A change introduced in `0.7.5` and reverted in `0.7.6` is
invisible to it. This is the retention problem the tool exists for, arriving in its smaller form:
capture is only as dense as the sessions that happen to start after an update.

## Retention

Keep every snapshot. They are ~520 KB of sorted text each, which packs small, and the whole point is
being able to diff across more than one update. Do not prune them to save space — the 2026-10-20
session is what pruning looks like.
