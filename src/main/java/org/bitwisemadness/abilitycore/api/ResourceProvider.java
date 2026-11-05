package org.bitwisemadness.abilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * How a resource is <em>accessed</em> (REQ-CORE-04, issue #88) - the read
 * half of the provider/consumer pair. One provider per resource id, declared
 * by whoever owns that resource's state; core ships health, hunger,
 * experience points and experience levels as built-ins. The paired
 * {@link ResourceConsumer} is the write half and <b>always requires its
 * provider first</b> (enforced at declaration).
 *
 * <p>Checks are pure server-side reads returning the {@link Affordability}
 * snapshot the pipeline validates and later hands to the consumer's charge -
 * validate-then-charge with no re-derivation, and the snapshot's amount is
 * the cost entry's amount. The fail-closed rule applies: a missing provider
 * refuses activation (payment cannot be reliably validated).</p>
 */
@FunctionalInterface
public interface ResourceProvider {

    /**
     * Read-only affordability check for one cost entry (no side effects,
     * server-side).
     *
     * @param abilityId the ability whose cost is being checked (diagnostics)
     * @param player    the paying player
     * @param amount    the cost entry's amount (&gt;= 0, finite - validated by {@link ResourceCost})
     * @return the snapshot; never null (null is a contract violation - the
     *         pipeline fails closed instead of trusting it)
     */
    Affordability check(ResourceLocation abilityId, ServerPlayer player, double amount);
}
