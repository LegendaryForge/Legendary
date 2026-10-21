package io.github.legendaryforge.legendary.core.api.weather;

/**
 * How a {@link WeatherId} resolves against the running engine's asset map.
 *
 * <p>{@code SENTINEL} exists because index 0 is a sentinel meaning "no override", and
 * {@code tryClaimOverride(ref, 0)} returns <b>true and does nothing</b> — measured 2026-10-20. The
 * port must reject it before the engine sees the call, or a caller receives a success it did not
 * get. Reporting it as a status rather than a number is what keeps the ordinal out of {@code core}.
 *
 * <p>{@code UNKNOWN} is distinguishable from {@code SENTINEL} only because the engine's asset map
 * returns {@code Integer.MIN_VALUE} rather than {@code 0} for a missing key — measured by
 * {@code /weatherportprobe}, and the reason this enum can have three values instead of two.
 */
public enum WeatherResolution {
    OK,
    UNKNOWN,
    SENTINEL
}
