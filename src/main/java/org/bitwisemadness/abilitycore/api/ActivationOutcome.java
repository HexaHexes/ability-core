package org.bitwisemadness.abilitycore.api;

/**
 * Result vocabulary of the activation pipeline (REQ-CORE-02).
 *
 * <p>The refusal contract: every {@code REFUSED_*} outcome guarantees
 * <b>zero consumption and zero execution</b> - the caller (consumers,
 * menu) owns messaging, and in combo context the ordinary weapon attack
 * proceeds (REQ-BR-08). {@link #PENDING} means the provider's native pipeline
 * took over (channelled/toggled execution); core never ticks costs itself.</p>
 *
 * <p>Consumers must handle every outcome - the enum is designed to be
 * switched over exhaustively without a {@code default} branch, so the
 * compiler enforces coverage (issue #13 acceptance).</p>
 */
public enum ActivationOutcome {

    /** The provider executed (or natively started) the ability. */
    ACTED,
    /** The provider's native pipeline owns ongoing execution. */
    PENDING,
    /** No definition registered for the id (saved references stay inert). */
    REFUSED_UNAVAILABLE,
    /** An eligibility/policy rule forbids this request. */
    REFUSED_RESTRICTED,
    /** Insufficient resources (pre-check; nothing was charged). */
    REFUSED_UNAFFORDABLE,
    /** The ability is on cooldown (pre-check; nothing was charged). */
    REFUSED_COOLDOWN,
    /** Target/context invalid for this ability. */
    REFUSED_INVALID_TARGET,
    /** No activation handler registered for this definition. */
    REFUSED_NO_ADAPTER;

    public boolean isRefusal() {
        return this != ACTED && this != PENDING;
    }

    public boolean isSuccess() {
        return this == ACTED || this == PENDING;
    }

    /**
     * Stable message key for caller-side rendering (translation keys are
     * finalized with the hotbar's message catalogue; keys are contract data,
     * not English strings).
     */
    public String messageKey() {
        return switch (this) {
            case ACTED -> "ability.activated";
            case PENDING -> "ability.pending";
            case REFUSED_UNAVAILABLE -> "ability.refused.unavailable";
            case REFUSED_RESTRICTED -> "ability.refused.restricted";
            case REFUSED_UNAFFORDABLE -> "ability.refused.unaffordable";
            case REFUSED_COOLDOWN -> "ability.refused.cooldown";
            case REFUSED_INVALID_TARGET -> "ability.refused.invalid_target";
            case REFUSED_NO_ADAPTER -> "ability.refused.no_adapter";
        };
    }
}
