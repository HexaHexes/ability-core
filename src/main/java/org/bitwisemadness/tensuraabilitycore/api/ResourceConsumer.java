package org.bitwisemadness.tensuraabilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A resource the ecosystem can check and charge (REQ-CORE-04).
 *
 * <p>One consumer per resource id (aura, magicule, mana, ...), declared by
 * whoever owns that resource's semantics - core owns none itself. Consumers
 * are pure server-side state machines over authoritative player state:</p>
 *
 * <ul>
 *   <li>{@link #validate} - read-only pre-check returning the
 *       {@link Affordability} snapshot; MUST NOT mutate state.</li>
 *   <li>{@link #charge} - called only after the ability's activation returned
 *       ACTED/PENDING, with the same snapshot; for EXTERNAL chargingModel
 *       abilities only (NATIVE providers charge in their own pipeline and
 *       register no action - ADR-002).</li>
 * </ul>
 */
public interface ResourceConsumer {

    /** Read-only affordability check (no side effects, server-side). */
    Affordability validate(ResourceLocation abilityId, ServerPlayer player);

    /**
     * Charges the validated snapshot after successful activation.
     * Implementations should treat the snapshot as authoritative (do not
     * re-derive amounts) and log-never-throw where a post-activation failure
     * would strand state (hardening detail tracked in core#15).
     */
    void charge(ResourceLocation abilityId, ServerPlayer player, Affordability validatedSnapshot);
}
