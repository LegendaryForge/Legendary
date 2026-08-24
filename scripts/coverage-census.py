#!/usr/bin/env python3
"""Aggregates per-module coverage reports into one verdict LINE.

Mirrors scripts/test-census.py: the verdict is content, not an exit code,
because exit status is positional and any wrapper swallows it. Retrieve it
with `grep COVERAGE_VERDICT`, never with `tail -1`.
"""
import json
import glob
import os
import sys
import time

from gradle_modules import discover_modules

# Derived from settings.gradle.kts, not hardcoded: a hardcoded list silently
# omits a new module while this script keeps reporting all-modules-GREEN.
MODULES = discover_modules()

rows = []
missing = []
oldest = None
for module in MODULES:
    matches = glob.glob(f"{module}/build/module-coverage.json")
    if not matches:
        missing.append(module)
        continue
    path = matches[0]
    with open(path, encoding="utf-8") as handle:
        rows.append(json.load(handle))
    mtime = os.path.getmtime(path)
    # min(), not max(): the verdict's freshness signal must reflect the
    # STALEST module's result, not the freshest. max() here would let a
    # single re-run of one module report a fresh age while the other
    # three modules' results are days old -- a failure in the green
    # direction, which is exactly what this gate exists to catch.
    oldest = mtime if oldest is None else min(oldest, mtime)

for row in rows:
    # Just the reason: the state itself is already the previous field, so repeating
    # the word here rendered ":mod:hytale 0/7 EXEMPT  EXEMPT (no Hytale server jar...)".
    #
    # Shown for EVERY non-FULL state that carries one, not only EXEMPT. A declared
    # module drifting into PARTIAL or EMPTY previously printed a bare "1/8 PARTIAL"
    # with its declaration nowhere on the line, so the reader could not tell a
    # declared gap from an undeclared one in exactly the states where that matters.
    suffix = f" ({row['reason']})" if row["state"] != "FULL" and row.get("reason") else ""
    print(f"{row['module']:22} {row['compiled']:3}/{row['onDisk']:<3} {row['state']}{suffix}")
for module in missing:
    print(f"{module:22} (no report -- run ./gradlew check)")

failed = [r for r in rows if r["state"] == "FAIL"]
partial = [r for r in rows if r["state"] == "PARTIAL"]
empty = [r for r in rows if r["state"] == "EMPTY"]

# An EMPTY module contributes nothing to the build, which is the same outcome as an
# undeclared zero-compile and is reached by an ordinary refactor: move every source out
# of a module and it reports 0/0 EMPTY with the build still SUCCESSFUL. Demonstrated
# 2026-08-24 by deleting mod/hytale/src/main/java -- guard green, census GREEN, nothing
# said a module had vanished.
#
# The guard's truth table tests EMPTY first and deliberately requires no declaration;
# changing that contradicts its approved spec and remains an open operator question.
# This is the census half, and it needs no new API: an exemption reason is already
# carried in the JSON, so a module declared with zeroCompileAllowedWhen stays green
# while one that simply lost its sources goes RED.
empty_undeclared = [r for r in empty if not r.get("reason")]
green = not failed and not missing and not empty_undeclared
exempt = sum(1 for r in rows if r["state"] == "EXEMPT")
age = "never run" if oldest is None else f"{int(time.time() - oldest)}s ago"
print(
    f"COVERAGE_VERDICT: {'GREEN' if green else 'RED'} | "
    f"{len(rows)}/{len(MODULES)} modules reported | {exempt} exempt | "
    f"{len(partial)} partial | {len(empty)} empty"
    f"{f' ({len(empty_undeclared)} undeclared)' if empty_undeclared else ''} | "
    f"{len(failed)} failing | "
    f"oldest result {age}"
)
sys.exit(0 if green else 1)
