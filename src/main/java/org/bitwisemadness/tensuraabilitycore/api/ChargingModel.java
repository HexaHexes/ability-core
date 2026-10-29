package org.bitwisemadness.tensuraabilitycore.api;

/**
 * Who charges for an ability (REQ-CORE-04, ADR-002).
 *
 * <p>Exactly one model per ability, declared once at handler registration.
 * The charging sites are mutually exclusive by construction:</p>
 *
 * <ul>
 *   <li>{@link #NATIVE} - the provider's own pipeline charges (ISS
 *       {@code AbstractSpell.castSpell}, Tensura {@code EnergyHelper} inside
 *       ability callbacks). Core never charges NATIVE abilities - the
 *       RESOURCE step still pre-validates affordability when a cost is
 *       declared, but no core charge can execute.</li>
 *   <li>{@link #EXTERNAL} - core charges after successful activation by
 *       calling the declared-cost resource's {@link ResourceConsumer#charge}
 *       with the validated snapshot. EXTERNAL abilities MUST declare a cost
 *       on their definition; {@code ResourceChargeStep} refuses the request
 *       <em>before</em> activation otherwise (an ability whose payment cannot
 *       be enforced must not activate - REQ-BR-08).</li>
 * </ul>
 */
public enum ChargingModel {

    /** Provider's pipeline charges; core never does (ADR-002). */
    NATIVE,

    /** Core charges via the cost's {@link ResourceConsumer} post-success; cost declaration mandatory. */
    EXTERNAL
}
