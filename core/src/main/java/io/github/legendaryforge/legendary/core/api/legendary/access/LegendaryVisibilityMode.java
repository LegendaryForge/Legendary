package io.github.legendaryforge.legendary.core.api.legendary.access;

/**
 * Controls who may spectate an encounter instance.
 *
 * <p>WORLD_VISIBLE: non-party bystanders may spectate (read-only), depending on platform constraints.
 * <p>INSTANCE_VISIBLE: only explicitly allowed spectators may observe (e.g., late joiners); platform may hide others.
 */
public enum LegendaryVisibilityMode {
    WORLD_VISIBLE,
    INSTANCE_VISIBLE
}
