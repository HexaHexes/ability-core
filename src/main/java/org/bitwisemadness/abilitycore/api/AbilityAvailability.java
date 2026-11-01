package org.bitwisemadness.abilitycore.api;

import java.util.Objects;

/**
 * The provider-neutral overlay data contract for one ability (REQ-HOT-08).
 *
 * <p>A slot's cooldown/cost/availability overlay is fed from this tuple and
 * nothing else. Each metric is option-typed via {@link Metric}: a provider that
 * does not expose a metric reports {@link Metric#unknown()}, and the overlay
 * renders no overlay for it - never a wrong value. Cooldown is a 0..1 progress
 * (0 = ready), cost reuses the declarative {@link ResourceCost}, and
 * availability is the three-state {@link AvailabilityState} verdict.</p>
 *
 * <p>Server-authoritative in origin: providers map already-synced or
 * contract-synced native state into this tuple once, at the provider boundary.
 * Consumers never import a provider class and the client never derives a value
 * it was not given.</p>
 *
 * @param cooldown     cooldown progress 0..1 (0 = ready), or unknown
 * @param cost         declared resource cost, or unknown
 * @param availability the provider's castability verdict, or unknown
 */
public record AbilityAvailability(
        Metric<Double> cooldown,
        Metric<ResourceCost> cost,
        AvailabilityState availability) {

    public AbilityAvailability {
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(availability, "availability");
        cooldown.knownValue().ifPresent(progress -> {
            if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0) {
                throw new IllegalArgumentException(
                        "cooldown progress must be finite and in [0,1], got " + progress);
            }
        });
    }

    /**
     * The no-metric tuple a synthetic or partial provider returns: every metric
     * unknown, so the overlay renders nothing and never lies (the issue's
     * fail-safe display requirement).
     */
    public static AbilityAvailability allUnknown() {
        return new AbilityAvailability(Metric.unknown(), Metric.unknown(), AvailabilityState.unknown());
    }

    /** @return true only when the provider affirmatively marked it available. */
    public boolean isAvailable() {
        return availability.isAvailable();
    }
}
