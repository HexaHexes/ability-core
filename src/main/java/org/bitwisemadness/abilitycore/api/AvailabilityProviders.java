package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Declared overlay availability providers, one per ability id (REQ-HOT-08).
 *
 * <p>The lookup surface the bar's overlay reads: {@link #availabilityFor} returns
 * the tuple for an ability, or {@link Optional#empty()} when no provider is
 * registered - which renders no overlay and never crashes (the missing-provider
 * acceptance criterion). A provider that returns null is a contract violation
 * logged once and degraded to {@link AbilityAvailability#allUnknown()}, so a
 * buggy provider cannot take down the overlay.</p>
 *
 * <p>Exactly one provider per ability id, declared by whoever owns that
 * ability's native state - duplicate declaration fails loudly (two providers
 * claiming one ability is the ambiguity that produces a wrong overlay).</p>
 */
public final class AvailabilityProviders {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, AvailabilityProvider> PROVIDERS = new ConcurrentHashMap<>();

    private AvailabilityProviders() {
    }

    /**
     * Declares the overlay provider for one ability id.
     *
     * @throws IllegalStateException if the ability already has a provider
     *         (loud ownership conflict, never silent overwrite)
     */
    public static void declare(ResourceLocation abilityId, AvailabilityProvider provider) {
        if (abilityId == null) {
            throw new IllegalArgumentException("abilityId must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null for " + abilityId);
        }
        AvailabilityProvider previous = PROVIDERS.putIfAbsent(abilityId, provider);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate availability provider for " + abilityId + " - two providers claim one ability");
        }
    }

    /**
     * Projects an ability's overlay tuple, or empty when no provider is
     * registered (render no overlay, no crash).
     *
     * @param player    the player to project (nullable for tests)
     * @param abilityId the ability to project
     * @return the tuple, or empty when no provider is declared
     */
    public static Optional<AbilityAvailability> availabilityFor(@Nullable Player player, ResourceLocation abilityId) {
        AvailabilityProvider provider = abilityId == null ? null : PROVIDERS.get(abilityId);
        if (provider == null) {
            return Optional.empty();
        }
        AbilityAvailability result = provider.availabilityFor(player, abilityId);
        if (result == null) {
            // contract violation: degrade to all-unknown so the overlay renders
            // nothing rather than crashing on a buggy provider
            LOGGER.warn("Availability provider for {} returned null - treating as all-unknown", abilityId);
            return Optional.of(AbilityAvailability.allUnknown());
        }
        return Optional.of(result);
    }

    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && PROVIDERS.containsKey(abilityId);
    }

    /** Test isolation: removes a declared provider. */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && PROVIDERS.remove(abilityId) != null;
    }
}
