# Legendary



Canonical legendary gameplay content built on LegendaryCore.


This repository provides a shared mod-level namespace and wiring layer, while each questline remains isolated and self-owned.

---

## Repository structure

- **Mod namespace:** `io.github.legendaryforge.legendary.mod`
- **Questline modules:** `io.github.legendaryforge.legendary.mod.<questline>`
  - Example: `io.github.legendaryforge.legendary.mod.stormseeker`

Each questline defines:
- Its own gate keys and denial reasons
- Its own wiring entrypoint
- Its own tests

---

## Wiring model

- `LegendaryWiring` is the mod-level aggregation entrypoint.
- Questlines expose their own wiring classes (e.g., `StormseekerWiring`).
- Today, wiring is limited to **gate registration**.

Activation, authority, and encounter creation remain intentionally phased and are not yet wired in LegendaryCore.

---

## Stormseeker (first questline)

Stormseeker is the first questline implemented under Legendary.

Current scope:
- Registers Stormseeker-specific activation gates into Core
- Defines stable gate keys and denial reason codes under the `stormseeker` namespace
- Validates wiring and propagation behavior via harness tests

Note: Under LegendaryCore `v1.0.0-rc3`, the ActivationService is intentionally inert and returns `legendarycore:not_wired`.

---

## Development

Legendary vendors LegendaryCore via an included build:

```text
vendor/LegendaryCore
```

Run tests:

```bash
./gradlew spotlessApply clean test
```

## License

MIT — see [LICENSE](LICENSE).


## Integration seams
- Canonical engine/ECS driving notes (tick entrypoints, participation semantics, and cleanup policy): `src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/integration/StormseekerEngineIntegrationNotes.java`
