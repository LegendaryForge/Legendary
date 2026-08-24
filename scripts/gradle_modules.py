#!/usr/bin/env python3
"""Derives the module list from settings.gradle.kts, the build's own authority.

Both census scripts previously hardcoded
    MODULES = ["core", "quests/stormseeker", "mod/hytale", "harness"]
in two places. A fifth module would have been invisible to both while they
carried on printing `4/4 modules reported ... GREEN` -- a failure in the green
direction, and the same shape as the zero-compile gap `checkModuleCoverage`
exists to catch. Deriving the list removes the drift rather than scheduling a
reminder to remember it.

An empty or unparseable settings file raises rather than returning [], because
an empty module list makes every downstream census vacuously green.
"""
import re
from pathlib import Path

# `include(":quests:stormseeker")` -> `quests/stormseeker`. Tolerates single or
# double quotes and surrounding whitespace; deliberately does NOT tolerate a
# missing match, see the guard below.
_INCLUDE = re.compile(r"""^\s*include\s*\(\s*['"]:([^'"]+)['"]\s*\)""", re.MULTILINE)


def discover_modules(root: Path | str = ".") -> list[str]:
    """Return module paths (e.g. 'quests/stormseeker') in settings.gradle.kts order."""
    settings = Path(root) / "settings.gradle.kts"
    if not settings.exists():
        raise SystemExit(f"cannot derive modules: {settings} not found (run from the repo root)")

    modules = [m.replace(":", "/") for m in _INCLUDE.findall(settings.read_text(encoding="utf-8"))]

    if not modules:
        # Loud, not silent: a parser that matches nothing would otherwise make
        # both census verdicts report GREEN over an empty module set.
        raise SystemExit(
            f"cannot derive modules: no include(\":...\") lines matched in {settings}. "
            "If the settings file changed shape, fix this parser -- do not fall back to a "
            "hardcoded list, which is the drift this module exists to remove."
        )
    return modules


if __name__ == "__main__":
    for module in discover_modules():
        print(module)
