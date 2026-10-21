package io.github.legendaryforge.legendary.core.api.weather;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WeatherKindsTest {

    @Test
    void isStorm_matches_storm_substring_case_insensitively() {
        assertTrue(WeatherKinds.isStorm(new WeatherId("Zone4_Thunder_Storm")));
        assertTrue(WeatherKinds.isStorm(new WeatherId("zone1_storm")));
        assertTrue(WeatherKinds.isStorm(new WeatherId("STORM")));
    }

    @Test
    void isStorm_rejects_non_storms() {
        assertFalse(WeatherKinds.isStorm(new WeatherId("Zone1_Rain")));
        assertFalse(WeatherKinds.isStorm(new WeatherId("Zone1_Cloudy_Medium")));
    }

    @Test
    void isThunderStorm_matches_both_spellings() {
        assertTrue(WeatherKinds.isThunderStorm(new WeatherId("Zone4_Thunder_Storm_Heavy")));
        assertTrue(WeatherKinds.isThunderStorm(new WeatherId("ThunderStorm")));
    }

    @Test
    void isThunderStorm_is_narrower_than_isStorm() {
        WeatherId plainStorm = new WeatherId("Zone1_Storm");
        assertTrue(WeatherKinds.isStorm(plainStorm));
        assertFalse(WeatherKinds.isThunderStorm(plainStorm));
    }

    @Test
    void null_is_not_any_kind() {
        assertFalse(WeatherKinds.isStorm(null));
        assertFalse(WeatherKinds.isThunderStorm(null));
    }
}
