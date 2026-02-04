package io.github.legendaryforge.legendary.mod.runtime;

/**
 * Minimal per-tick context supplied by the host runtime.
 *
 * <p>Do not assume real time; this is deliberately tick-based.
 */
public record LegendaryTickContext(long tick) {}
