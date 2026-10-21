package io.github.legendaryforge.legendary.core.api.weather;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of a claim attempt. A record rather than a boolean, following
 * {@code ActivationAttemptResult}/{@code ActivationAttemptStatus} and {@code GateDecision}.
 *
 * @param holder present only for {@link WeatherClaimStatus#REFUSED_HELD}, naming who holds it
 */
public record WeatherClaimResult(WeatherClaimStatus status, Optional<OverrideOwner> holder) {

    public WeatherClaimResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(holder, "holder");
    }

    public static WeatherClaimResult of(WeatherClaimStatus status) {
        return new WeatherClaimResult(status, Optional.empty());
    }

    public static WeatherClaimResult refusedHeldBy(OverrideOwner holder) {
        return new WeatherClaimResult(WeatherClaimStatus.REFUSED_HELD, Optional.of(holder));
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }
}
