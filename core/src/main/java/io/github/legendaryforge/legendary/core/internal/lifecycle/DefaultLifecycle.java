package io.github.legendaryforge.legendary.core.internal.lifecycle;

import io.github.legendaryforge.legendary.core.api.lifecycle.Lifecycle;
import io.github.legendaryforge.legendary.core.api.lifecycle.LifecyclePhase;
import java.util.*;

public final class DefaultLifecycle implements Lifecycle {

    private LifecyclePhase phase = LifecyclePhase.BOOTSTRAP;
    private final EnumMap<LifecyclePhase, List<Runnable>> callbacks = new EnumMap<>(LifecyclePhase.class);

    public DefaultLifecycle() {
        for (LifecyclePhase p : LifecyclePhase.values()) {
            callbacks.put(p, new ArrayList<>());
        }
    }

    @Override
    public synchronized LifecyclePhase phase() {
        return phase;
    }

    @Override
    public synchronized void onPhase(LifecyclePhase target, Runnable callback) {
        Objects.requireNonNull(target, "phase");
        Objects.requireNonNull(callback, "callback");

        // If target phase is current or already passed, run immediately.
        if (phase.compareTo(target) >= 0) {
            callback.run();
            return;
        }

        callbacks.get(target).add(callback);
    }

    /**
     * Transition to the next phase. This is internal-only.
     * Enforced ordering: phases can only move forward.
     */
    public synchronized void advanceTo(LifecyclePhase next) {
        Objects.requireNonNull(next, "next");
        if (next.compareTo(phase) < 0) {
            throw new IllegalStateException("Cannot move lifecycle backwards from " + phase + " to " + next);
        }
        if (next == phase) {
            return;
        }

        phase = next;

        // Execute callbacks registered for this phase
        List<Runnable> list = callbacks.get(next);
        if (list != null && !list.isEmpty()) {
            // Copy to avoid modification during iteration
            List<Runnable> toRun = new ArrayList<>(list);
            list.clear();
            toRun.forEach(Runnable::run);
        }
    }
}
