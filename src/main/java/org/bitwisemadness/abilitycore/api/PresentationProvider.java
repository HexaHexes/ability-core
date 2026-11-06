package org.bitwisemadness.abilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The provider boundary for menu presentation (core#122).
 *
 * <p>A provider maps its native, already-synced state into one
 * {@link AbilityPresentation} per ability, once, here - consumers never see
 * a provider class. Live values only (learning progress, toggle state):
 * implementations must be side-disciplined - read synced or
 * contract-synced values, never mutate state, and report nothing (no
 * presentation) rather than a guessed field.</p>
 *
 * <p>Returning null is a contract violation - the registry degrades it to
 * "no presentation" with a logged line, so a buggy provider renders an
 * unchanged row instead of crashing the menu.</p>
 */
@FunctionalInterface
public interface PresentationProvider {

    /**
     * Builds the presentation for one ability.
     *
     * @param player    the player whose state is being projected (may be null
     *                  only in tests; production always passes a live player)
     * @param abilityId the ability to present
     * @return the presentation; never null in a conforming implementation
     */
    AbilityPresentation presentFor(@Nullable Player player, ResourceLocation abilityId);
}
