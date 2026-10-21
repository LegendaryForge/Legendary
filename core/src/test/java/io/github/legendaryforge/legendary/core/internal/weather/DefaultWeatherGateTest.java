package io.github.legendaryforge.legendary.core.internal.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultWeatherGateTest {

    private static final PlayerRef ALICE = new PlayerRef("alice");
    private static final WeatherId RAIN = new WeatherId("Zone1_Rain");
    private static final WeatherId CLOUDY = new WeatherId("Zone1_Cloudy_Medium");
    private static final OverrideOwner OWNER = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));

    @Test
    void with_no_claim_effective_is_what_the_world_gives_them() {
        FakeWeatherBackend backend = new FakeWeatherBackend();
        backend.withTracker("alice");
        backend.setUnoverridden("alice", CLOUDY);

        WeatherGate gate = new DefaultWeatherGate(backend, new OverrideRegistry());

        assertEquals(Optional.of(CLOUDY), gate.effective(ALICE));
    }

    @Test
    void a_claim_overrides_effective_but_never_unoverridden() {
        FakeWeatherBackend backend = new FakeWeatherBackend();
        backend.withTracker("alice");
        backend.setUnoverridden("alice", CLOUDY);
        OverrideRegistry registry = new OverrideRegistry();
        registry.put(ALICE, OWNER, RAIN);

        WeatherGate gate = new DefaultWeatherGate(backend, registry);

        assertEquals(Optional.of(RAIN), gate.effective(ALICE), "the claim wins");
        assertEquals(Optional.of(CLOUDY), gate.unoverridden(ALICE), "the world's own weather is untouched");
    }

    @Test
    void a_subject_with_no_tracker_reports_empty_even_with_a_claim() {
        FakeWeatherBackend backend = new FakeWeatherBackend();
        backend.withoutTracker("alice");
        OverrideRegistry registry = new OverrideRegistry();
        registry.put(ALICE, OWNER, RAIN);

        WeatherGate gate = new DefaultWeatherGate(backend, registry);

        assertEquals(Optional.empty(), gate.effective(ALICE));
        assertEquals(Optional.empty(), gate.unoverridden(ALICE));
    }

    @Test
    void rejects_nulls() {
        WeatherGate gate = new DefaultWeatherGate(new FakeWeatherBackend(), new OverrideRegistry());
        assertThrows(NullPointerException.class, () -> gate.effective(null));
        assertThrows(NullPointerException.class, () -> gate.unoverridden(null));
        assertThrows(NullPointerException.class, () -> new DefaultWeatherGate(null, new OverrideRegistry()));
        assertThrows(NullPointerException.class, () -> new DefaultWeatherGate(new FakeWeatherBackend(), null));
    }
}
