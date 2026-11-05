package org.bitwisemadness.abilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The per-player icon projection boundary (core#62), mirroring
 * {@link AvailabilityProvider}: one function per ability id that answers the
 * texture <em>this</em> player should see (e.g. the tier of the core actually
 * carried), where {@link AbilityDefinition#iconId} can only name one static
 * texture for everyone.
 *
 * <p>Implementations are render-time and <b>client-state only</b>: read only
 * client-known state (synced stacks, attachments, the local player) and never
 * mutate anything. Declaring a provider must never trigger or require a
 * server sync - icons are display-only, resolved by the texture lookup at
 * draw time. Returning null means "I have no answer" and falls to the next
 * step of the documented resolution order (the definition's static icon);
 * throwing is caught, logged once per ability and treated the same way.</p>
 */
@FunctionalInterface
public interface IconProvider {

    /**
     * The icon for one ability and player.
     *
     * @param player    the player whose state to project (may be null only in
     *                  tests; production passes the player being rendered)
     * @param abilityId the ability to project
     * @return the icon id, or null when this provider has no answer (never a
     *         guess - absence falls down the resolution order)
     */
    @Nullable
    ResourceLocation iconFor(@Nullable Player player, ResourceLocation abilityId);
}
