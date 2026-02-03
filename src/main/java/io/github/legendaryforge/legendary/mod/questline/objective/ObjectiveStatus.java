package io.github.legendaryforge.legendary.mod.questline.objective;

import java.util.Objects;

/**
 * Engine-agnostic objective status snapshot for UI/hosts.
 *
 * <p>Intentionally minimal: stable id, completion flag, and an optional hint for next action.
 */
public record ObjectiveStatus(String id, boolean completed, String hint) {

    public ObjectiveStatus {
        Objects.requireNonNull(id, "id");
    }

    public static ObjectiveStatus complete(String id) {
        return new ObjectiveStatus(id, true, null);
    }

    public static ObjectiveStatus incomplete(String id, String hint) {
        return new ObjectiveStatus(id, false, hint);
    }
}
