# Legendary

[![CI](https://github.com/LegendaryForge/Legendary/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/LegendaryForge/Legendary/actions/workflows/ci.yml)

Questline content for [Hytale](https://hytale.com), built as a single Gradle multi-project
in Java 25.

---

## Modules

```
core/                 engine-agnostic foundation — primitives, encounter framework,
                      questline framework (SPI, registry, objectives, runtime seams)
quests/stormseeker/   one questline; depends on core and nothing else
mod/hytale/           the only module that touches com.hypixel.* — plugin entrypoint,
                      commands, per-questline adapters
harness/              consumer-side contract tests
```

The boundary rule is mechanical: **`core` holds frameworks, `quests/<name>/` holds *uses* of
them.** For any file, ask whether it knows about a specific questline. A second questline is
a new `quests/<name>/` depending on `core` and nothing else.

That rule is enforced by the build rather than by convention:

| Task | Asserts |
|---|---|
| `:core:checkNoPlatformImports` | no `com.hypixel.` under `core/src` |
| `:core:checkNoQuestlineDependency` | `core` declares no dependency on any `quests:*` |
| `:mod:hytale:checkHytaleJarVersion` | the game jar's class-file version matches the build target |
| `checkModuleCoverage` | no module silently compiles zero of its sources |

All of them hang off `check`, so `./gradlew build` runs them.

---

## Building

```bash
./gradlew build
```

The JDK is provisioned automatically — `gradle/gradle-daemon-jvm.properties` pins the daemon
to Temurin 25 via foojay resolution, so a fresh clone needs no local JDK setup.

Two census scripts report structured verdicts:

```bash
python3 scripts/test-census.py       # CENSUS_VERDICT:   test counts per module
python3 scripts/coverage-census.py   # COVERAGE_VERDICT: compiled/on-disk per module
```

Both print a named verdict line. Retrieve it with `grep CENSUS_VERDICT`, not `tail -1` —
position is not a reliable channel, because anything that wraps the command can append
output after the verdict.

### Without Hytale installed

`mod/hytale` compiles against the game's `HytaleServer.jar`. When that jar is absent the
module excludes its Hytale sources and the rest of the build proceeds normally, so
**the project builds fine on a machine with no game installed** — `core`,
`quests/stormseeker` and `harness` all compile and test.

This is reported, not hidden: `coverage-census.py` prints
`:mod:hytale 0/7 EXEMPT (no Hytale server jar in this environment)` on every such run. An
*undeclared* zero-compile in any module fails the build instead.

CI runs on GitHub-hosted runners, which cannot have the proprietary jar. **CI therefore does
not verify the Hytale integration layer** — it verifies the other three modules, lint and
formatting across all four, and that no module goes dark undeclared. Changes to
`mod/hytale` need a local build against a real game install.

To point the build at a non-default install:

```bash
./gradlew build -Phytale_home=/path/to/Hytale
```

---

## Stormseeker

The first and currently only questline. Its logic lives in `quests/stormseeker/`, engine-
agnostic; the Hytale-specific adapters live in `mod/hytale/`.

The questline framework in `core` (`QuestlineModule` SPI, registry, `LegendaryWiring`) is in
place but **not yet adopted** — `LegendaryHytalePlugin` still wires Stormseeker imperatively,
so adding a second questline is not yet a single registration call.
[`docs/architecture/questline-framework-adoption.md`](docs/architecture/questline-framework-adoption.md)
records the preconditions and the migration.

---

## Documentation

- [`docs/stormseeker/stormseeker-canonical.md`](docs/stormseeker/stormseeker-canonical.md) — the canonical questline design
- [`docs/architecture/questline-framework-adoption.md`](docs/architecture/questline-framework-adoption.md) — current module boundaries and the adoption plan
- [`docs/architecture/ecs-principles.md`](docs/architecture/ecs-principles.md), [`testing-strategy.md`](docs/architecture/testing-strategy.md)

Documents dated before 2026-08-24 describe a five-repository layout that no longer exists;
`docs/architecture/repository-structure.md` carries a staleness banner for that reason.

---

## License

MIT — see [LICENSE](LICENSE).
