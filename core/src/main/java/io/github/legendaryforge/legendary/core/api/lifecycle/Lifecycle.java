package io.github.legendaryforge.legendary.core.api.lifecycle;

public interface Lifecycle {

    LifecyclePhase phase();

    /**
     * Register a callback to run when the given phase is entered.
     * If the phase is already active or has already passed, the callback is executed immediately.
     */
    void onPhase(LifecyclePhase phase, Runnable callback);
}
