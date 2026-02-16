package io.github.legendaryforge.legendary.mod.stormseeker.client;

import io.github.legendaryforge.legendary.core.api.event.Event;

/**
 * Authority: v2.0 Canonical Document
 * Manages the "Leyline Sight" (Electric Blue vision) client-side state.
 */
public final class PerceptionToggleHandler {

    /**
     * Fired when the 30-second ritual in StormseekerAttunementService completes.
     * Records are implicitly static.
     */
    public record AttunementCompleteEvent(String playerId) implements Event {}

    // Initializing to false explicitly silences the "never assigned" warning.
    private boolean leylineSightActive = false;

    /**
     * Toggles the spectral lens view.
     * Default Keybind: 'V'
     */
    public void toggleLeylineSight() {
        this.leylineSightActive = !this.leylineSightActive;
        // Logic for post-processing shaders will be implemented in Phase D.
        System.out.println("Leyline Sight Toggled: " + this.leylineSightActive);
    }

    public boolean isActive() {
        return leylineSightActive;
    }
}
