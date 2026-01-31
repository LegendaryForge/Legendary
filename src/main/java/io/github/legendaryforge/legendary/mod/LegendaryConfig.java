package io.github.legendaryforge.legendary.mod;

/**
 * Minimal configuration surface for the Legendary mod.
 *
 * <p>This is intentionally in-memory only for now (no file parsing yet). It exists so wiring can
 * be toggled per questline without splitting mods or duplicating shared logic.
 */
public record LegendaryConfig(boolean stormseekerEnabled) {

    public static LegendaryConfig defaults() {
        return new LegendaryConfig(true);
    }

    public static LegendaryConfig allDisabled() {
        return new LegendaryConfig(false);
    }
}
