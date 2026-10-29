package org.bitwisemadness.tensuraabilitycore.api;

import org.jetbrains.annotations.Nullable;

/**
 * Result of the activation pipeline (REQ-CORE-02).
 *
 * <p>Immutability rule: outcomes are facts reported after the fact; the
 * refusal guarantee (no consumption, no execution) is enforced by the
 * pipeline's ordering, not by inspecting this object.</p>
 *
 * @param outcome    the outcome, never null
 * @param messageKey stable translation key for callers; null means "use the
 *                   outcome's default key" ({@link ActivationOutcome#messageKey()})
 */
public record ActivationResult(ActivationOutcome outcome, @Nullable String messageKey) {

    public static final ActivationResult ACTED = new ActivationResult(ActivationOutcome.ACTED, null);
    public static final ActivationResult PENDING = new ActivationResult(ActivationOutcome.PENDING, null);

    public ActivationResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
    }

    public static ActivationResult refused(ActivationOutcome refusal) {
        if (!refusal.isRefusal()) {
            throw new IllegalArgumentException(refusal + " is not a refusal outcome");
        }
        return new ActivationResult(refusal, null);
    }

    public static ActivationResult refused(ActivationOutcome refusal, String messageKey) {
        if (!refusal.isRefusal()) {
            throw new IllegalArgumentException(refusal + " is not a refusal outcome");
        }
        return new ActivationResult(refusal, messageKey);
    }

    /** The effective message key (specific override, else the outcome default). */
    public String effectiveMessageKey() {
        return messageKey != null ? messageKey : outcome.messageKey();
    }

    public boolean isRefusal() {
        return outcome.isRefusal();
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }
}
