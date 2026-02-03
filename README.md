# Legendary

Legendary is the canonical gameplay and questline mod built on top of LegendaryCore.

This repository hosts Legendary questlines (starting with Stormseeker) and the content-side systems that exercise LegendaryCores encounter and activation primitives.

---

## Scope

- Questline modules and wiring (enable/disable by questline)
- Content-side runtime systems and host integration seams
- Stormseeker questline implementation (phased)

Legendary intentionally avoids embedding platform-specific engine code. Platform adapters should remain isolated behind explicit seams.

---

## Repositories

- **LegendaryCore**: shared deterministic encounter foundation
- **LegendaryContent**: dogfooding and validation mod for harness-style testing

---

## Status

Under active development.

---

## License

MIT License. See `LICENSE`.
