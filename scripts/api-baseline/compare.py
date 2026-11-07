#!/usr/bin/env python3
"""Compare two api-baseline snapshots, separating ENGINE change from OUR import-surface change.

WHY THIS EXISTS: the README used to recommend `diff -ru snapshots/<old> snapshots/<new>`.
capture.sh derives its type list from current sources on every run -- deliberately, so it
tracks our real dependency surface -- which means that between two captures the list moves
with OUR imports. On 2026-11-07 the 0.7.4 -> 0.7.6 raw diff was 299 lines, ALL additions,
and every one was a type we had begun importing in PRs #146-#149 after the 0.7.4 capture.
Engine change restricted to the 65 types present in both snapshots was zero. A raw diff
fails informative-looking, which is worse than failing loud: it reads as two releases of
engine growth.

The type list is derived from signatures.txt's `===== ` headers rather than a sidecar file,
so this works on every snapshot already committed -- no backfill, and no second place for
the list to drift out of.

Prints a verdict LINE, read it by name. Exit status is positional and any wrapper swallows it.

Exit 0 for a successful comparison whether or not the engine changed; 2 if the comparison
could not be made. An engine change is the FINDING, not a failure -- making it exit
non-zero would train readers to ignore the verdict, which is the reasoning test-census.py
records for using a floor rather than strict equality.

LIMITS ARE INHERITED. This narrows exactly one failure mode. Every caveat in README.md
"What it does NOT cover" still applies: inner classes, behaviour-at-identical-signature,
assets, and @RestrictedApi are all invisible here too.
"""
import difflib
import pathlib
import sys


def parse_signatures(path):
    """signatures.txt -> {fully.qualified.Type: javap block}."""
    blocks, current, buf = {}, None, []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.startswith("===== "):
            if current is not None:
                blocks[current] = "\n".join(buf).strip()
            current, buf = line[6:].strip(), []
        elif current is not None:
            buf.append(line)
    if current is not None:
        blocks[current] = "\n".join(buf).strip()
    return blocks


def read_lines(path):
    return [l for l in path.read_text(encoding="utf-8", errors="replace").splitlines() if l.strip()]


def identity_of(snapshot):
    path = snapshot / "identity.txt"
    if not path.exists():
        return snapshot.name
    fields = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if ": " in line:
            key, value = line.split(": ", 1)
            fields[key.strip()] = value.strip()
    version = fields.get("Implementation-Version", "?")
    revision = fields.get("Implementation-Revision-Id", "")[:8]
    return f"{version} rev {revision}" if revision else version


def main(argv):
    if len(argv) != 3:
        print(f"usage: {pathlib.Path(argv[0]).name} <old-snapshot-dir> <new-snapshot-dir>")
        print("COMPARE_VERDICT: FAILED | wrong number of arguments")
        return 2

    old, new = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    for snapshot in (old, new):
        for required in ("classes.txt", "signatures.txt"):
            if not (snapshot / required).exists():
                print(f"missing {snapshot / required}")
                print(f"COMPARE_VERDICT: FAILED | {snapshot.name} is not a snapshot directory")
                return 2

    print(f"old: {old.name}   ({identity_of(old)})")
    print(f"new: {new.name}   ({identity_of(new)})")

    # --- ENGINE: top-level class inventory ------------------------------------
    old_classes, new_classes = set(read_lines(old / "classes.txt")), set(read_lines(new / "classes.txt"))
    removed_classes = sorted(old_classes - new_classes)
    added_classes = sorted(new_classes - old_classes)

    print(f"\n=== ENGINE: top-level com.hypixel classes ({len(old_classes)} -> {len(new_classes)}) ===")
    if not removed_classes and not added_classes:
        print("  unchanged -- identical sets")
    for name in removed_classes:
        print(f"  - {name}")
    for name in added_classes:
        print(f"  + {name}")

    # --- ENGINE: signatures, restricted to the types in BOTH lists ------------
    old_sigs, new_sigs = parse_signatures(old / "signatures.txt"), parse_signatures(new / "signatures.txt")
    shared = sorted(set(old_sigs) & set(new_sigs))
    changed = [t for t in shared if old_sigs[t] != new_sigs[t]]

    print(f"\n=== ENGINE: signatures over the {len(shared)} type(s) present in BOTH snapshots ===")
    if not changed:
        print("  unchanged -- no signature differs")
    for name in changed:
        became_unresolved = "UNRESOLVED" in new_sigs[name] and "UNRESOLVED" not in old_sigs[name]
        flag = "  <-- TYPE IS GONE FROM THE JAR" if became_unresolved else ""
        print(f"\n  ##### {name}{flag}")
        for line in difflib.unified_diff(
            old_sigs[name].splitlines(), new_sigs[name].splitlines(), lineterm="", n=1
        ):
            if not line.startswith(("---", "+++")):
                print(f"    {line}")

    # --- OURS: the type list itself moved ------------------------------------
    dropped_imports = sorted(set(old_sigs) - set(new_sigs))
    gained_imports = sorted(set(new_sigs) - set(old_sigs))

    print(f"\n=== OURS, NOT THE ENGINE'S: import surface ({len(old_sigs)} -> {len(new_sigs)} types) ===")
    print("  capture.sh derives this list from mod/hytale sources at capture time, so these")
    print("  entries reflect OUR imports changing between captures -- not engine change.")
    if not dropped_imports and not gained_imports:
        print("  unchanged -- same type list")
    for name in dropped_imports:
        print(f"  - no longer imported: {name}")
    for name in gained_imports:
        unresolved = " (UNRESOLVED in the new jar!)" if "UNRESOLVED" in new_sigs[name] else ""
        print(f"  + newly imported:     {name}{unresolved}")

    # An import we gained that does not resolve is an engine fact, not an import fact.
    gained_unresolved = [t for t in gained_imports if "UNRESOLVED" in new_sigs[t]]

    engine_changed = bool(removed_classes or added_classes or changed or gained_unresolved)
    print()
    print(
        f"COMPARE_VERDICT: {'ENGINE_CHANGED' if engine_changed else 'ENGINE_UNCHANGED'}"
        f" | classes {len(old_classes)}->{len(new_classes)} +{len(added_classes)}/-{len(removed_classes)}"
        f" | shared types {len(shared)}, {len(changed)} changed"
        f" | our imports +{len(gained_imports)}/-{len(dropped_imports)}"
        f"{f', {len(gained_unresolved)} unresolved' if gained_unresolved else ''}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
