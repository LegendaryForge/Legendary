package io.github.legendaryforge.legendary.content.encounter.toystorm;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class PhaseMachine {

    private final AtomicReference<EncounterPhase> current = new AtomicReference<>();

    public boolean enter(EncounterPhase next, Consumer<EncounterPhase> onEnter) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(onEnter, "onEnter");

        while (true) {
            EncounterPhase prev = current.get();
            if (prev == next) {
                return false;
            }
            if (current.compareAndSet(prev, next)) {
                onEnter.accept(next);
                return true;
            }
        }
    }

    public EncounterPhase current() {
        return current.get();
    }
}
