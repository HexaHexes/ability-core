package org.bitwisemadness.abilitycore.api;

/**
 * When a declared ability contributes experience (REQ-CORE-12). A spec's
 * trigger set is its declared intent: {@link MasteryAwards#award} ignores
 * a trigger the spec did not declare, so a domain firing {@link #HIT}
 * grants nothing to an ability that only declared {@link #ACTIVATION}.
 *
 * <p>Core auto-fires only {@link #ACTIVATION} (post-activation, in the
 * pipeline). {@link #HIT}, {@link #KILL} and {@link #CUSTOM} are fired by
 * the domain that can observe them, through the same award entry point.</p>
 */
public enum MasteryTrigger {
    /** The ability activated successfully (core fires this one). */
    ACTIVATION,
    /** The ability damaged a target (the domain fires this one). */
    HIT,
    /** The ability killed a target (the domain fires this one). */
    KILL,
    /** Anything else a domain wants to name (quest credit, discovery, ...). */
    CUSTOM
}
