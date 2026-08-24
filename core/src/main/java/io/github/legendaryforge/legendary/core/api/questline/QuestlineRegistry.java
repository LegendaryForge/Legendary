package io.github.legendaryforge.legendary.core.api.questline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Holds the questlines a running server ships.
 *
 * <p>Deliberately an instance rather than a static list: core must not name any questline, so
 * membership is decided by the composition root (the platform mod entrypoint) and passed in.
 * Adding a questline is a {@code register} call there, not an edit here.
 */
public final class QuestlineRegistry {

    private final List<QuestlineModule> modules = new ArrayList<>();

    public QuestlineRegistry register(QuestlineModule module) {
        Objects.requireNonNull(module, "module");
        String id = Objects.requireNonNull(module.id(), "module.id()");
        for (QuestlineModule existing : modules) {
            if (existing.id().equals(id)) {
                throw new IllegalArgumentException("Questline id already registered: " + id);
            }
        }
        modules.add(module);
        return this;
    }

    /**
     * Registered questlines, in registration order.
     *
     * <p>This is a live unmodifiable view over the registry's backing list, not a copy: it
     * reflects any {@link #register} calls made after this method returns.
     */
    public List<QuestlineModule> all() {
        return Collections.unmodifiableList(modules);
    }
}
