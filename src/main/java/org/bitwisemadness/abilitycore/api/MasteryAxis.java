package org.bitwisemadness.abilitycore.api;

/**
 * The four effect axes mastery can modify (REQ-CORE-12). Each axis is
 * optional per ability (a spec's effect map is the "any combination"
 * choice), carries core's default curve when no custom handler is named,
 * and is applied by whoever owns the semantics: core prices the
 * {@link #COST} axis inside {@code ResourceChargeStep}; {@link #CAST_TIME},
 * {@link #POWER} and {@link #DURATION} are read through
 * {@link MasteryEffects} and applied by the domain's own pipeline.
 */
public enum MasteryAxis {
    /** Casting cost reduction - multiplier {@code <= 1}. */
    COST,
    /** Casting time reduction - multiplier {@code <= 1}. */
    CAST_TIME,
    /** Casting power increase - multiplier {@code >= 1}. */
    POWER,
    /** Casting duration increase - multiplier {@code >= 1}. */
    DURATION
}
