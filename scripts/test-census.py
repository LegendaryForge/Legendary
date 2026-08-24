#!/usr/bin/env python3
"""Counts JUnit tests per Gradle module from test-result XML.

The 187-test invariant is load-bearing during the module boundary realignment,
so it is executable rather than remembered. Prints a verdict LINE, not just an
exit code -- exit status is positional and any wrapper swallows it.
"""
import glob
import os
import re
import sys
import time

EXPECTED = int(os.environ.get("EXPECTED_TESTS", "196"))
MODULES = ["core", "quests/stormseeker", "mod/hytale", "harness"]

total = failed = 0
newest = 0.0
for module in MODULES:
    files = glob.glob(f"{module}/build/test-results/test/TEST-*.xml")
    if not files:
        print(f"{module:22} (no results)")
        continue
    tests = bad = 0
    for path in files:
        head = open(path, encoding="utf-8", errors="replace").read(2000)
        for pattern, target in (("tests", "t"), ("failures", "f"), ("errors", "f")):
            match = re.search(rf'{pattern}="(\d+)"', head)
            value = int(match.group(1)) if match else 0
            if target == "t":
                tests += value
            else:
                bad += value
        newest = max(newest, os.path.getmtime(path))
    print(f"{module:22} tests={tests:4} failures+errors={bad}  classes={len(files)}")
    total += tests
    failed += bad

age = "never run" if newest == 0 else f"{int(time.time() - newest)}s ago"
green = total == EXPECTED and failed == 0
print(f"{'TOTAL':22} tests={total:4} failures+errors={failed}  (expected {EXPECTED})")
print(f"CENSUS_VERDICT: {'GREEN' if green else 'RED'} | {total} tests | {failed} failures | newest result {age}")
sys.exit(0 if green else 1)
