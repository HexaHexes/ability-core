package org.bitwisemadness.abilitycore.api;

import org.jetbrains.annotations.Nullable;

/**
 * Result of a resource affordability pre-check (REQ-CORE-04).
 *
 * <p>Three-state by design: the brief forbids activating when payment cannot
 * be <em>reliably validated</em> - so "unknown" is not "maybe yes", it is a
 * fail-closed state the pipeline maps to REFUSED_UNAFFORDABLE. The snapshot
 * handed to {@link ResourceConsumer#charge} is this object: charging validates
 * against exactly what was checked (REQ-CORE-02's validate-then-charge).</p>
 *
 * @param resourceId the resource checked (e.g. {@code tensura:aura})
 * @param amount     the amount that was validated (>= 0, finite)
 * @param state      SUFFICIENT / INSUFFICIENT / UNKNOWN
 */
public record Affordability(String resourceId, double amount, State state) {

    public enum State {
        /** Enough resources - charge may proceed after successful activation. */
        SUFFICIENT,
        /** Not enough - refuse before activation, nothing consumed. */
        INSUFFICIENT,
        /** No consumer for the resource, or consumer cannot answer - refuse (fail closed). */
        UNKNOWN
    }

    public Affordability {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("amount must be finite and >= 0, got " + amount);
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
    }

    public static Affordability sufficient(String resourceId, double amount) {
        return new Affordability(resourceId, amount, State.SUFFICIENT);
    }

    public static Affordability insufficient(String resourceId, double amount) {
        return new Affordability(resourceId, amount, State.INSUFFICIENT);
    }

    /** Unknown with a zero declared amount (nothing validated, nothing chargeable). */
    public static Affordability unknown(String resourceId) {
        return new Affordability(resourceId, 0.0, State.UNKNOWN);
    }

    public boolean isSufficient() {
        return state == State.SUFFICIENT;
    }

    /** Convenience for callers that only branch on the boolean form. */
    @Nullable
    public Boolean sufficientFlag() {
        return switch (state) {
            case SUFFICIENT -> Boolean.TRUE;
            case INSUFFICIENT -> Boolean.FALSE;
            case UNKNOWN -> null;
        };
    }
}
