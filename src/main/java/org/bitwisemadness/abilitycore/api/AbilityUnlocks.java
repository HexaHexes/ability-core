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
 * Declared unlock providers, one per ability id (issue #117, findings #116
 * recommendation): the per-player "does the player have this ability?" query
 * abstracted away from upstream mods - the {@link AvailabilityProviders}
 * mirror with an option-typed answer.
 *
 * <p>{@link #isLearned} returns {@link Optional#empty()} when no provider is
 * declared (and a provider may abstain the same way): <b>unknown renders and
 * validates as unknown - never a guessed boolean</b> (REQ-HOT-08
 * discipline). {@code true} = learned/unlocked, {@code false} = not.</p>
 *
 * <p><b>The provider event-subscription invariant</b> (findings #116):
 * providers answer <i>live</i> (the query never caches), act on their
 * upstream's own learn/unlock events (their side effects - e.g. clearing
 * stale assignments), and reconcile at login so an event-less mutation path
 * cannot leave the two disagreeing. Core stores the provider function, never
 * learned state - no per-player duplication of upstream truth, no payload in
 * v1 (upstream learned state is client-readable; a provider that cannot
 * answer client-side abstains to unknown instead).</p>
 *
 * <p>Exactly one provider per ability id, declared by whoever owns that
 * ability's learning semantics - duplicate declaration fails loudly (two
 * providers claiming one ability is the ambiguity that produces a wrong
 * verdict). Upstream types live only inside provider implementations: no
 * signature here names a mod other than Minecraft's (the leak-gate rule).
 * The locked-list read (a separate upstream concept) is deferred until its
 * semantics are pinned (findings #116 unverified item 1).</p>
 */
public final class AbilityUnlocks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * A per-player unlock answer for one ability.
     */
    @FunctionalInterface
    public interface UnlockProvider {

        /**
         * @param player    the asking player (nullable for tests - a
         *                  provider may abstain when it cannot answer)
         * @param abilityId the ability being asked about
         * @return true = learned, false = not learned, empty = unknown
         */
        Optional<Boolean> learnedFor(@Nullable Player player, ResourceLocation abilityId);
    }

    private static final Map<ResourceLocation, UnlockProvider> PROVIDERS = new ConcurrentHashMap<>();

    private AbilityUnlocks() {
    }

    /**
     * Declares the unlock provider for one ability id, at mod setup (same
     * timing contract as the other declare registries - the query runs
     * afterwards).
     *
     * @throws IllegalStateException if the ability already has a provider
     *         (loud ownership conflict, never silent overwrite)
     */
    public static void declare(ResourceLocation abilityId, UnlockProvider provider) {
        if (abilityId == null) {
            throw new IllegalArgumentException("abilityId must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null for " + abilityId);
        }
        UnlockProvider previous = PROVIDERS.putIfAbsent(abilityId, provider);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate unlock provider for " + abilityId + " - two providers claim one ability");
        }
    }

    /**
     * Whether the player has the ability - or unknown when no provider
     * answers (undeclared id, abstaining provider, or a contract-violating
     * null return, logged once). Consumers render and validate unknown as
     * unknown; they never guess.
     *
     * @param player    the player to ask (nullable for tests)
     * @param abilityId the ability to ask about
     * @return true = learned, false = not learned, empty = unknown
     */
    public static Optional<Boolean> isLearned(@Nullable Player player, ResourceLocation abilityId) {
        UnlockProvider provider = abilityId == null ? null : PROVIDERS.get(abilityId);
        if (provider == null) {
            return Optional.empty();
        }
        Optional<Boolean> result = provider.learnedFor(player, abilityId);
        if (result == null) {
            // contract violation: degrade to unknown rather than letting a
            // buggy provider produce a guessed verdict
            LOGGER.warn("Unlock provider for {} returned null - treating as unknown", abilityId);
            return Optional.empty();
        }
        return result;
    }

    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && PROVIDERS.containsKey(abilityId);
    }

    /** Test isolation: removes a declared provider. */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && PROVIDERS.remove(abilityId) != null;
    }
}
