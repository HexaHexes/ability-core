package org.bitwisemadness.abilitycore.api;

import java.util.Objects;

/**
 * A provider's availability verdict for one ability (REQ-HOT-08).
 *
 * <p>Three states by design, mirroring the option-typed tuple the issue
 * selects: {@link Available} means the provider can affirm the ability is
 * castable now, {@link Unavailable} carries a player-facing reason it is not,
 * and {@link Unknown} is the explicit "this provider cannot answer" state. The
 * brief forbids a wrong overlay: {@link Unknown} renders as no overlay, never a
 * guessed grey-out - so a provider that exposes no availability logic reports
 * {@link Unknown} instead of lying.</p>
 */
public sealed interface AvailabilityState {

    /** The provider affirms the ability is castable now. */
    record Available() implements AvailabilityState {
    }

    /**
     * The provider affirms the ability is not castable, with a player-facing
     * reason (e.g. {@code "cooldown"}, {@code "insufficient_mana"}).
     *
     * @param reason non-blank display reason key
     */
    record Unavailable(String reason) implements AvailabilityState {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("unavailable reason must not be blank");
            }
        }
    }

    /** The provider does not expose availability logic (render no overlay). */
    record Unknown() implements AvailabilityState {
    }

    static AvailabilityState available() {
        return new Available();
    }

    static AvailabilityState unavailable(String reason) {
        return new Unavailable(reason);
    }

    static AvailabilityState unknown() {
        return new Unknown();
    }

    /** @return true only when the provider affirmed availability (never on unknown). */
    default boolean isAvailable() {
        return this instanceof Available;
    }
}
