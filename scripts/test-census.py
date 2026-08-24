#!/usr/bin/env python3
"""Counts JUnit tests per Gradle module from test-result XML.

The EXPECTED-test invariant (see EXPECTED below) is load-bearing during the
module boundary realignment, so it is executable rather than remembered.
Prints a verdict LINE, not just an exit code -- exit status is positional and
any wrapper swallows it.
"""
import glob
import os
import re
import sys
import time

EXPECTED = int(os.environ.get("EXPECTED_TESTS", "204"))
MODULES = ["core", "quests/stormseeker", "mod/hytale", "harness"]

total = failed = 0
oldest = None
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
        mtime = os.path.getmtime(path)
        # min(), not max(): the verdict's freshness signal must reflect the
        # STALEST module's result, not the freshest. max() here would let a
        # single re-run of one module report a fresh age while the other
        # three modules' results are days old -- a failure in the green
        # direction, which is exactly what this gate exists to catch.
        oldest = mtime if oldest is None else min(oldest, mtime)
    print(f"{module:22} tests={tests:4} failures+errors={bad}  classes={len(files)}")
    total += tests
    failed += bad

age = "never run" if oldest is None else f"{int(time.time() - oldest)}s ago"
green = total == EXPECTED and failed == 0
print(f"{'TOTAL':22} tests={total:4} failures+errors={failed}  (expected {EXPECTED})")
print(f"CENSUS_VERDICT: {'GREEN' if green else 'RED'} | {total} tests | {failed} failures | oldest result {age}")
sys.exit(0 if green else 1)
