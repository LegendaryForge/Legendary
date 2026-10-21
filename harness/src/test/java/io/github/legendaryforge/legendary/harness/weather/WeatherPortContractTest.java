package io.github.legendaryforge.legendary.harness.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherKinds;
import io.github.legendaryforge.legendary.core.api.weather.WeatherReleaseStatus;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultPlayerWeatherController;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultWeatherGate;
import io.github.legendaryforge.legendary.core.internal.weather.OverrideRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Consumer-side contract: what a questline may rely on from the weather port. */
class WeatherPortContractTest {

    private static final PlayerRef ALICE = new PlayerRef("alice");
    private static final WeatherId CALM = new WeatherId("Zone1_Cloudy_Medium");
    private static final WeatherId STORM = new WeatherId("Zone4_Thunder_Storm");
    private static final OverrideOwner QUESTLINE = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));
    private static final OverrideOwner OTHER = new OverrideOwner(ResourceId.parse("legendary:other_questline"));

    private RecordingWeatherBackend backend;
    private WeatherGate gate;
    private PlayerWeatherController controller;

    @BeforeEach
    void setUp() {
        backend = new RecordingWeatherBackend();
        backend.setEnvironmentWeather("alice", CALM);
        OverrideRegistry registry = new OverrideRegistry();
        gate = new DefaultWeatherGate(backend, registry);
        controller = new DefaultPlayerWeatherController(backend, registry);
    }

    @Test
    void a_questline_can_storm_gate_one_player_without_touching_the_world() {
        assertFalse(WeatherKinds.isStorm(gate.effective(ALICE).orElseThrow()));

        assertEquals(WeatherClaimStatus.GRANTED, controller.claim(ALICE, STORM, QUESTLINE).status());

        assertTrue(WeatherKinds.isStorm(gate.effective(ALICE).orElseThrow()));
        assertFalse(
                WeatherKinds.isStorm(gate.unoverridden(ALICE).orElseThrow()),
                "the world's own weather must be unchanged");
    }

    @Test
    void releasing_restores_what_the_player_would_otherwise_see() {
        controller.claim(ALICE, STORM, QUESTLINE);

        assertEquals(WeatherReleaseStatus.RELEASED, controller.release(ALICE, QUESTLINE));
        assertEquals(Optional.of(CALM), gate.effective(ALICE));
    }

    @Test
    void two_questlines_cannot_silently_overwrite_each_other() {
        controller.claim(ALICE, STORM, QUESTLINE);

        var refused = controller.claim(ALICE, CALM, OTHER);

        assertEquals(WeatherClaimStatus.REFUSED_HELD, refused.status());
        assertEquals(Optional.of(QUESTLINE), refused.holder());
        assertTrue(
                WeatherKinds.isStorm(gate.effective(ALICE).orElseThrow()),
                "the first questline's gate must survive the second's attempt");
    }

    @Test
    void the_port_never_asks_the_engine_for_a_last_sent_value() {
        controller.claim(ALICE, STORM, QUESTLINE);
        gate.effective(ALICE);
        gate.unoverridden(ALICE);

        assertTrue(
                backend.calls().stream().noneMatch(call -> call.startsWith("weatherIndex")),
                "reads must resolve, never read the send-log");
    }
}
