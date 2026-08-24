#!/usr/bin/env python3
"""Counts JUnit tests per Gradle module from test-result XML.

EXPECTED is a FLOOR, not an equality: the count may rise freely but must never
fall. Strict equality failed every commit that added a test, which trains
readers to ignore the verdict -- the precise failure this gate exists to avoid.
Prints a verdict LINE, not just an exit code -- exit status is positional and
any wrapper swallows it.
"""
import glob
import os
import re
import sys
import time

from gradle_modules import discover_modules

EXPECTED = int(os.environ.get("EXPECTED_TESTS", "204"))
# Derived from settings.gradle.kts, not hardcoded: a hardcoded list silently
# omits a new module while this script keeps reporting all-modules-GREEN.
MODULES = discover_modules()

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
green = total >= EXPECTED and failed == 0
print(f"{'TOTAL':22} tests={total:4} failures+errors={failed}  (expected at least {EXPECTED})")
print(f"CENSUS_VERDICT: {'GREEN' if green else 'RED'} | {total} tests | {failed} failures | oldest result {age}")
sys.exit(0 if green else 1)
