package io.github.legendaryforge.legendary.core.api.weather;

/** Outcome of a {@link PlayerWeatherController#claim} attempt. */
public enum WeatherClaimStatus {

    /** No owner held the subject; the override is now ours. */
    GRANTED,

    /** We already held it; the weather was changed. Mirrors the engine's measured behaviour. */
    UPDATED,

    /** Another owner holds it. The held weather is untouched. */
    REFUSED_HELD,

    /** The backend does not recognise the weather id. */
    UNKNOWN_WEATHER,

    /** The id resolves to the index-0 sentinel, which cannot be claimed. */
    UNSUPPORTED_WEATHER,

    /** The subject has no weather tracker — absent, invalid, or not connected. */
    SUBJECT_UNAVAILABLE;

    public boolean isSuccess() {
        return this == GRANTED || this == UPDATED;
    }
}
