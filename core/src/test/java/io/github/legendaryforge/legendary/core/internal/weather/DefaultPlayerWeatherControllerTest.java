package io.github.legendaryforge.legendary.core.internal.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimResult;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherReleaseStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPlayerWeatherControllerTest {

    private static final PlayerRef ALICE = new PlayerRef("alice");
    private static final WeatherId STORM = new WeatherId("Zone4_Thunder_Storm");
    private static final WeatherId RAIN = new WeatherId("Zone1_Rain");
    private static final WeatherId NOT_A_WEATHER = new WeatherId("Nonexistent_Weather");
    private static final WeatherId SENTINEL_WEATHER = new WeatherId("Index_Zero");

    private static final OverrideOwner STORMSEEKER = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));
    private static final OverrideOwner ENCOUNTER = new OverrideOwner(ResourceId.parse("legendary:encounter"));

    private FakeWeatherBackend backend;
    private PlayerWeatherController controller;

    @BeforeEach
    void setUp() {
        backend = new FakeWeatherBackend();
        backend.withTracker("alice");
        backend.register(STORM, WeatherResolution.OK);
        backend.register(RAIN, WeatherResolution.OK);
        backend.register(SENTINEL_WEATHER, WeatherResolution.SENTINEL);
        controller = new DefaultPlayerWeatherController(backend, new OverrideRegistry());
    }

    @Test
    void granted_when_nobody_holds_it() {
        WeatherClaimResult result = controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherClaimStatus.GRANTED, result.status());
        assertTrue(result.isSuccess());
        assertEquals(STORM, backend.overrideOf("alice"));
        assertEquals(Optional.of(STORMSEEKER), controller.holder(ALICE));
    }

    /**
     * Re-claims with a DIFFERENT INSTANCE carrying the same value, which is the whole point. A test
     * reusing the same constant passes against an implementation that compares owners by reference
     * identity -- and reference identity is precisely the engine bug OverrideOwner exists to avoid.
     * Found by mutation: swapping equals() for != left the original version of this test green.
     */
    @Test
    void updated_when_the_same_owner_reclaims() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        OverrideOwner sameOwnerDifferentInstance = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));
        assertNotSame(STORMSEEKER, sameOwnerDifferentInstance, "the test is pointless if these are the same object");

        WeatherClaimResult result = controller.claim(ALICE, RAIN, sameOwnerDifferentInstance);

        assertEquals(WeatherClaimStatus.UPDATED, result.status());
        assertTrue(result.isSuccess());
        assertEquals(RAIN, backend.overrideOf("alice"));
    }

    @Test
    void refused_held_when_a_different_owner_claims_and_the_weather_is_untouched() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        WeatherClaimResult result = controller.claim(ALICE, RAIN, ENCOUNTER);

        assertEquals(WeatherClaimStatus.REFUSED_HELD, result.status());
        assertEquals(Optional.of(STORMSEEKER), result.holder());
        assertEquals(STORM, backend.overrideOf("alice"), "the held weather must not change");
        assertEquals(1, backend.applyCalls(), "a refused claim must not reach the engine");
    }

    @Test
    void unknown_weather_is_rejected_before_the_engine_is_touched() {
        WeatherClaimResult result = controller.claim(ALICE, NOT_A_WEATHER, STORMSEEKER);

        assertEquals(WeatherClaimStatus.UNKNOWN_WEATHER, result.status());
        assertEquals(0, backend.applyCalls());
        assertEquals(Optional.empty(), controller.holder(ALICE));
    }

    @Test
    void the_sentinel_is_rejected_before_the_engine_is_touched() {
        WeatherClaimResult result = controller.claim(ALICE, SENTINEL_WEATHER, STORMSEEKER);

        assertEquals(WeatherClaimStatus.UNSUPPORTED_WEATHER, result.status());
        assertEquals(0, backend.applyCalls(), "claiming the sentinel returns true and no-ops; never send it");
        assertEquals(Optional.empty(), controller.holder(ALICE));
    }

    @Test
    void subject_unavailable_when_there_is_no_tracker() {
        backend.withoutTracker("alice");

        WeatherClaimResult result = controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherClaimStatus.SUBJECT_UNAVAILABLE, result.status());
        assertEquals(0, backend.applyCalls());
    }

    @Test
    void a_failed_engine_apply_does_not_record_an_owner() {
        backend.failNextApply();

        WeatherClaimResult result = controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherClaimStatus.SUBJECT_UNAVAILABLE, result.status());
        assertEquals(Optional.empty(), controller.holder(ALICE), "no owner may be recorded for a failed apply");
    }

    @Test
    void the_owner_can_release() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherReleaseStatus.RELEASED, controller.release(ALICE, STORMSEEKER));
        assertEquals(1, backend.clearCalls());
        assertEquals(Optional.empty(), controller.holder(ALICE));
    }

    @Test
    void a_foreign_release_is_refused_and_clears_nothing() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherReleaseStatus.REFUSED_NOT_OWNER, controller.release(ALICE, ENCOUNTER));
        assertEquals(0, backend.clearCalls());
        assertEquals(Optional.of(STORMSEEKER), controller.holder(ALICE));
    }

    @Test
    void releasing_an_unheld_subject_reports_not_held() {
        assertEquals(WeatherReleaseStatus.NOT_HELD, controller.release(ALICE, STORMSEEKER));
        assertEquals(0, backend.clearCalls());
    }

    @Test
    void the_owner_releasing_a_vanished_subject_still_drops_the_registry_entry() {
        controller.claim(ALICE, STORM, STORMSEEKER);
        backend.withoutTracker("alice");

        assertEquals(WeatherReleaseStatus.SUBJECT_UNAVAILABLE, controller.release(ALICE, STORMSEEKER));
        assertEquals(Optional.empty(), controller.holder(ALICE), "a departed player must not strand an owner");
    }

    @Test
    void forget_drops_the_owner_so_the_next_claim_is_granted() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        controller.forget(ALICE);

        assertEquals(Optional.empty(), controller.holder(ALICE));
        assertEquals(
                WeatherClaimStatus.GRANTED,
                controller.claim(ALICE, RAIN, ENCOUNTER).status());
    }

    @Test
    void forget_does_not_touch_the_engine() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        controller.forget(ALICE);

        assertEquals(0, backend.clearCalls(), "forget is registry-only; the engine self-heals a stale owner");
    }

    @Test
    void rejects_nulls() {
        assertThrows(
                NullPointerException.class, () -> new DefaultPlayerWeatherController(null, new OverrideRegistry()));
        assertThrows(NullPointerException.class, () -> new DefaultPlayerWeatherController(backend, null));
        assertThrows(NullPointerException.class, () -> controller.claim(null, STORM, STORMSEEKER));
        assertThrows(NullPointerException.class, () -> controller.claim(ALICE, null, STORMSEEKER));
        assertThrows(NullPointerException.class, () -> controller.claim(ALICE, STORM, null));
        assertThrows(NullPointerException.class, () -> controller.release(null, STORMSEEKER));
        assertThrows(NullPointerException.class, () -> controller.release(ALICE, null));
        assertThrows(NullPointerException.class, () -> controller.forget(null));
        assertThrows(NullPointerException.class, () -> controller.holder(null));
    }
}
