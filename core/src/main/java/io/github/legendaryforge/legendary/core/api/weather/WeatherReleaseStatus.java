package io.github.legendaryforge.legendary.core.api.weather;

/** Outcome of a {@link PlayerWeatherController#release} attempt. */
public enum WeatherReleaseStatus {

    /** We held it and the override is cleared. */
    RELEASED,

    /** Nobody held an override for this subject. */
    NOT_HELD,

    /** Someone else holds it; nothing was cleared. */
    REFUSED_NOT_OWNER,

    /**
     * We held it but the subject has no tracker. The registry entry is dropped anyway, so a departed
     * player cannot strand an owner.
     */
    SUBJECT_UNAVAILABLE
}
