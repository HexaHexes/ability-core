package org.bitwisemadness.abilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A {@link ResourceConsumer} that can give a charged snapshot back
 * (issue #107): the inverse of {@link #charge}, owned by the same pair
 * owner that owns the charge.
 *
 * <p>An integrating mod that routes payments through someone else's consumer
 * cannot invert a charge it did not implement (a Ley Lines balance, a
 * battlewills pool) - asking the owner through
 * {@link ResourceConsumers#refund} is the only way a refused-after-charge
 * action stops eating the player's payment. {@code refund} receives the
 * <b>exact {@link Affordability} snapshot that was charged</b> - never a
 * recomputed amount - so charge and refund cannot drift; each owner keeps
 * its inverse exactly where it keeps its charge.</p>
 *
 * <p>Declaration is unchanged: a refundable consumer is declared through the
 * normal {@code ResourceConsumers.declare} (it <em>is</em> a
 * {@link ResourceConsumer} - the pairing rule and the timing contract do not
 * move). Non-refundable consumers stay legal; the refund path answers them
 * loudly (see {@link ResourceConsumers#refund}).</p>
 */
public interface RefundableConsumer extends ResourceConsumer {

    /**
     * Gives back one charged snapshot.
     *
     * @param abilityId the ability that was charged
     * @param player    the player whose pool is restored
     * @param snapshot  the exact snapshot {@link #charge} received
     */
    void refund(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot);
}
