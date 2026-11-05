package org.bitwisemadness.abilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A resource the ecosystem can <em>consume</em> as an ability cost
 * (REQ-CORE-04, issue #88) - the write half of the provider/consumer pair.
 *
 * <p>One consumer per resource id (aura, magicule, mana, ...), declared by
 * whoever owns that resource's deduction semantics - core owns none itself
 * beyond its four built-ins. <b>A consumer always requires its
 * {@link ResourceProvider}</b>: {@link ResourceConsumers#declare} rejects a
 * resource id whose provider is not yet declared, so validation (read) and
 * deduction (write) can never drift apart. Consumers are pure server-side
 * state machines:</p>
 *
 * <ul>
 *   <li>The pipeline runs the provider's {@link ResourceProvider#check}
 *       first and hands the validated {@link Affordability} snapshot here
 *       verbatim (validate-then-charge, no re-derivation).</li>
 *   <li>{@link #charge} - called only after the ability's activation returned
 *       ACTED/PENDING, for EXTERNAL chargingModel abilities only (NATIVE
 *       providers charge in their own pipeline - ADR-002).</li>
 * </ul>
 */
public interface ResourceConsumer {

    /**
     * Charges the validated snapshot after successful activation.
     * Implementations should treat the snapshot as authoritative (do not
     * re-derive amounts) and log-never-throw where a post-activation failure
     * would strand state (hardening detail tracked in core#15).
     */
    void charge(ResourceLocation abilityId, ServerPlayer player, Affordability validatedSnapshot);
}
