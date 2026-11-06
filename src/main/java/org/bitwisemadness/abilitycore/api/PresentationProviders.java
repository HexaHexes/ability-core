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
 * Declared menu-presentation providers, one per ability id (core#122).
 *
 * <p>The lookup surface the menu reads: {@link #present} answers the
 * provider's {@link AbilityPresentation} for an ability, or
 * {@link Optional#empty()} when no provider is registered (the menu renders
 * the unchanged base row/tooltip - unknown never crashes and never guesses).
 * A provider that returns null is a contract violation, logged and degraded
 * to "no presentation".</p>
 *
 * <p>Exactly one provider per ability id, declared by whoever owns that
 * ability's native state - duplicate declaration fails loudly (two
 * providers claiming one ability produces a contradictory menu).</p>
 */
public final class PresentationProviders {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, PresentationProvider> PROVIDERS =
            new ConcurrentHashMap<>();

    private PresentationProviders() {
    }

    /**
     * Declares the presentation provider for one ability id.
     *
     * @throws IllegalArgumentException on null id or null provider
     * @throws IllegalStateException    if the ability already has a provider
     *         (loud ownership conflict, never silent overwrite)
     */
    public static void declare(ResourceLocation abilityId, PresentationProvider provider) {
        if (abilityId == null) {
            throw new IllegalArgumentException("abilityId must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null for " + abilityId);
        }
        PresentationProvider previous = PROVIDERS.putIfAbsent(abilityId, provider);
        if (previous != null) {
            throw new IllegalStateException("Duplicate presentation provider for " + abilityId
                    + " - two providers claim one ability");
        }
    }

    /**
     * The presentation for one ability, or empty when nothing is declared
     * (or the provider violated the contract - see above).
     *
     * @param player    the player whose state to present (nullable in tests)
     * @param abilityId the ability to present
     * @return the presentation; empty = render the base row only
     */
    public static Optional<AbilityPresentation> present(@Nullable Player player,
            ResourceLocation abilityId) {
        PresentationProvider provider = abilityId == null ? null : PROVIDERS.get(abilityId);
        if (provider == null) {
            return Optional.empty();
        }
        AbilityPresentation result = provider.presentFor(player, abilityId);
        if (result == null) {
            // contract violation: no presentation beats a crashing menu
            LOGGER.warn("Presentation provider for {} returned null - treating as no presentation",
                    abilityId);
            return Optional.empty();
        }
        return Optional.of(result);
    }

    /** Existence query for consumer-side gating and fixtures. */
    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && PROVIDERS.containsKey(abilityId);
    }

    /** Test isolation / future reload paths. */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && PROVIDERS.remove(abilityId) != null;
    }
}
