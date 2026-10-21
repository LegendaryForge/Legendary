package io.github.legendaryforge.legendary.core.api.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeatherClaimResultTest {

    private static final OverrideOwner OWNER = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));

    @Test
    void of_carries_no_holder() {
        WeatherClaimResult result = WeatherClaimResult.of(WeatherClaimStatus.GRANTED);
        assertEquals(WeatherClaimStatus.GRANTED, result.status());
        assertEquals(Optional.empty(), result.holder());
    }

    @Test
    void refusedHeldBy_names_the_holder() {
        WeatherClaimResult result = WeatherClaimResult.refusedHeldBy(OWNER);
        assertEquals(WeatherClaimStatus.REFUSED_HELD, result.status());
        assertEquals(Optional.of(OWNER), result.holder());
    }

    @Test
    void granted_and_updated_are_the_only_successes() {
        assertTrue(WeatherClaimResult.of(WeatherClaimStatus.GRANTED).isSuccess());
        assertTrue(WeatherClaimResult.of(WeatherClaimStatus.UPDATED).isSuccess());
        assertFalse(WeatherClaimResult.of(WeatherClaimStatus.UNKNOWN_WEATHER).isSuccess());
        assertFalse(
                WeatherClaimResult.of(WeatherClaimStatus.UNSUPPORTED_WEATHER).isSuccess());
        assertFalse(
                WeatherClaimResult.of(WeatherClaimStatus.SUBJECT_UNAVAILABLE).isSuccess());
        assertFalse(WeatherClaimResult.refusedHeldBy(OWNER).isSuccess());
    }

    @Test
    void owner_equality_is_by_value_not_identity() {
        OverrideOwner same = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));
        assertEquals(OWNER, same);
        assertNotSame(OWNER, same);
    }

    @Test
    void rejects_nulls() {
        assertThrows(NullPointerException.class, () -> new OverrideOwner(null));
        assertThrows(NullPointerException.class, () -> new WeatherClaimResult(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new WeatherClaimResult(WeatherClaimStatus.GRANTED, null));
    }
}
