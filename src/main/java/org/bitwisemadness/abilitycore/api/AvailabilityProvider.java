package org.bitwisemadness.abilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The provider boundary for the overlay data contract (REQ-HOT-08).
 *
 * <p>A provider maps its native, already-synced state into one
 * {@link AbilityAvailability} tuple per ability, once, here - consumers never
 * see a provider class. A provider that does not expose a given metric returns
 * that metric as {@link Metric#unknown()} / {@link AvailabilityState#unknown()}
 * rather than a guess, so the overlay renders no overlay instead of a wrong
 * value.</p>
 *
 * <p>Implementations must be side-disciplined: read only synced or
 * contract-synced values (the client never holds authority), and never mutate
 * state. Returning null is a contract violation - the registry degrades it to
 * {@link AbilityAvailability#allUnknown()} so a buggy provider cannot crash the
 * overlay.</p>
 */
@FunctionalInterface
public interface AvailabilityProvider {

    /**
     * Builds the overlay tuple for one ability.
     *
     * @param player    the player whose state is being projected (may be null
     *                  only in tests; production always passes a live player)
     * @param abilityId the ability to project
     * @return the tuple; never null in a conforming implementation
     */
    AbilityAvailability availabilityFor(@Nullable Player player, ResourceLocation abilityId);
}
