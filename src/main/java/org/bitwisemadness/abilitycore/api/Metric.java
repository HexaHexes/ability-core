package org.bitwisemadness.abilitycore.api;

import java.util.Optional;

/**
 * A single overlay metric a provider may or may not expose (REQ-HOT-08).
 *
 * <p>Two states by design: {@link Known} carries a real value, {@link Unknown}
 * is the explicit "this provider does not expose this metric" state. The
 * distinction matters for the fail-safe display the brief demands: a cooldown of
 * {@code 0} means "ready", while {@link Unknown} means "we cannot tell" - the
 * overlay renders no overlay for {@link Unknown} rather than a wrong value.</p>
 *
 * <p>Providers map their native state into this at the provider boundary, once;
 * consumers never see a raw {@code null} standing in for "unknown".</p>
 *
 * @param <T> the metric's value type (e.g. {@code Double} progress,
 *            {@link ResourceCost})
 */
public sealed interface Metric<T> {

    /**
     * The provider exposes this metric with a real, validated value.
     *
     * @param value the metric value; must not be null
     */
    record Known<T>(T value) implements Metric<T> {
        public Known {
            if (value == null) {
                throw new IllegalArgumentException("known metric value must not be null");
            }
        }
    }

    /**
     * The provider does not expose this metric (render no overlay; never a
     * guessed or zeroed value).
     */
    record Unknown<T>() implements Metric<T> {
    }

    /** Marks a metric the provider exposes. */
    static <T> Metric<T> known(T value) {
        return new Known<>(value);
    }

    /** Marks a metric the provider does not expose. */
    static <T> Metric<T> unknown() {
        return new Unknown<>();
    }

    /** @return true when the provider exposed a real value. */
    default boolean isKnown() {
        return this instanceof Known<?>;
    }

    /**
     * @return the exposed value, or empty when the provider did not expose the
     *         metric - never a default or guess
     */
    @SuppressWarnings("unchecked")
    default Optional<T> knownValue() {
        if (this instanceof Known<?> known) {
            return Optional.of((T) known.value());
        }
        return Optional.empty();
    }
}
