package io.github.legendaryforge.legendary.mod.stormseeker.client;

import java.util.Objects;

/**
 * Handles the client-side 'Leyline Sight' perception toggle.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Permanent Unlock: Listens for AttunementCompleteEvent to unlock the ability.</li>
 *   <li>Input Management: Toggles state via KeyBinding (default 'V') if unlocked.</li>
 *   <li>Indefinite Toggle: State persists until manually toggled off.</li>
 *   <li>Visual & Audio Manifestation: Applies color grading, particle visibility, and audio loop.</li>
 *   <li>Optimization: All effects are client-side only.</li>
 * </ul>
 */
public class PerceptionToggleHandler {

    // Placeholder types for platform-specific concepts
    public record AttunementCompleteEvent(String playerId) {}
    public record KeyInputEvent(int key, boolean pressed) {}
    public record ClientTickEvent() {}

    // Placeholder EventBus interface (will be replaced with LegendaryCore dependency)
    public interface EventBus {}

    // Placeholder Client/World interfaces
    public interface Client {
        String getLocalPlayerId();
        boolean isKeyDown(int key);
        void setPostProcessColorGrade(String gradeId, boolean active);
        void setParticleVisibility(String particleType, boolean visible);
        void playLoopingAudio(String audioId, float volume);
        void stopLoopingAudio(String audioId);
    }

    public interface PlayerCapability {
        boolean hasLeylineSight();
        void unlockLeylineSight();
    }

    private static final int KEY_V = 86; // GLFW_KEY_V or similar
    private static final String COLOR_GRADE_ELECTRIC_BLUE = "electric_blue";
    private static final String PARTICLE_LEYLINE_HARMONIC = "leyline_harmonic";
    private static final String AUDIO_ATMOSPHERIC_HUM = "atmospheric_hum";

    private final EventBus eventBus;
    private final Client client;
    private final PlayerCapability capability; // Injected or retrieved via player

    private boolean isSightActive = false;
    private boolean wasVPressed = false;

    public PerceptionToggleHandler(EventBus eventBus, Client client, PlayerCapability capability) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.client = Objects.requireNonNull(client, "client");
        this.capability = Objects.requireNonNull(capability, "capability");

        // eventBus.subscribe(AttunementCompleteEvent.class, this::onAttunementComplete);
        // eventBus.subscribe(KeyInputEvent.class, this::onKeyInput);
        // eventBus.subscribe(ClientTickEvent.class, this::onClientTick);
    }

    public void onAttunementComplete(AttunementCompleteEvent event) {
        if (event.playerId().equals(client.getLocalPlayerId())) {
            capability.unlockLeylineSight();
        }
    }

    public void onKeyInput(KeyInputEvent event) {
        if (event.key() == KEY_V) {
            if (event.pressed() && !wasVPressed) {
                if (capability.hasLeylineSight()) {
                    toggleSight();
                }
            }
            wasVPressed = event.pressed();
        }
    }

    private void toggleSight() {
        isSightActive = !isSightActive;
        updateEffects();
    }

    private void updateEffects() {
        if (isSightActive) {
            client.setPostProcessColorGrade(COLOR_GRADE_ELECTRIC_BLUE, true);
            client.setParticleVisibility(PARTICLE_LEYLINE_HARMONIC, true);
            client.playLoopingAudio(AUDIO_ATMOSPHERIC_HUM, 0.5f);
        } else {
            client.setPostProcessColorGrade(COLOR_GRADE_ELECTRIC_BLUE, false);
            client.setParticleVisibility(PARTICLE_LEYLINE_HARMONIC, false);
            client.stopLoopingAudio(AUDIO_ATMOSPHERIC_HUM);
        }
    }

    // Ensure effects persist or are re-applied if needed (e.g. world change)
    public void onClientTick(ClientTickEvent event) {
        // Optional: Check if effects need re-application
    }
}
