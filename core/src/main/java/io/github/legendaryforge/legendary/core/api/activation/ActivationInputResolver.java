package io.github.legendaryforge.legendary.core.api.activation;

/**
 * Resolves authoritative activation inputs (attributes/target context) server-side.
 */
@FunctionalInterface
public interface ActivationInputResolver {

    ActivationInput resolve(ActivationService.ActivationAttemptRequest request);
}
