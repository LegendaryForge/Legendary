package io.github.legendaryforge.legendary.mod.runtime;

/**
 * Legendary-owned runtime registration seam.
 *
 * <p>This repository does not assume engine ECS/scheduler types directly. The host/plugin adapts
 * this registrar to the engine's scheduling/ECS mechanism.
 */
public interface LegendarySystemRegistrar {

    void register(Object system);
}
