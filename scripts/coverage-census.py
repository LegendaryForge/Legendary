#!/usr/bin/env python3
"""Aggregates per-module coverage reports into one verdict LINE.

Mirrors scripts/test-census.py: the verdict is content, not an exit code,
because exit status is positional and any wrapper swallows it. Retrieve it
with `grep COVERAGE_VERDICT`, never with `tail -1`.
"""
import json
import glob
import sys

MODULES = ["core", "quests/stormseeker", "mod/hytale", "harness"]

rows = []
missing = []
for module in MODULES:
    matches = glob.glob(f"{module}/build/module-coverage.json")
    if not matches:
        missing.append(module)
        continue
    with open(matches[0], encoding="utf-8") as handle:
        rows.append(json.load(handle))

for row in rows:
    suffix = f"  EXEMPT ({row['reason']})" if row["state"] == "EXEMPT" else ""
    print(f"{row['module']:22} {row['compiled']:3}/{row['onDisk']:<3} {row['state']}{suffix}")
for module in missing:
    print(f"{module:22} (no report -- run ./gradlew check)")

failed = [r for r in rows if r["state"] == "FAIL"]
green = not failed and not missing
exempt = sum(1 for r in rows if r["state"] == "EXEMPT")
print(
    f"COVERAGE_VERDICT: {'GREEN' if green else 'RED'} | "
    f"{len(rows)}/{len(MODULES)} modules reported | {exempt} exempt | {len(failed)} failing"
)
sys.exit(0 if green else 1)
