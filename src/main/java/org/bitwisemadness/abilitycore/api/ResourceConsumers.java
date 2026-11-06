package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Declared resource consumers, one per resource id (REQ-CORE-04).
 *
 * <p>Resources are declared by their owner (battlewills declares aura, a
 * provider declares its own pool) - duplicate declaration for one resource id
 * fails loudly: two owners for one currency is exactly the ambiguity that
 * produces double-charging.</p>
 */
public final class ResourceConsumers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, ResourceConsumer> CONSUMERS = new ConcurrentHashMap<>();

    private ResourceConsumers() {
    }

    /**
     * Declares the consumer for one resource id.
     *
     * @throws IllegalArgumentException if the resource id is null/blank, the
     *         consumer is null, or the resource's <b>provider is not yet
     *         declared</b> (a consumer always requires its provider - issue #88)
     * @throws IllegalStateException if the resource id already has a consumer
     *         (loud ownership conflict, never silent overwrite)
     */
    public static void declare(String resourceId, ResourceConsumer consumer) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null for " + resourceId);
        }
        if (!ResourceProviders.has(resourceId)) {
            throw new IllegalArgumentException(
                    "resource provider not declared for '" + resourceId
                            + "' - declare the provider first (a consumer always requires its provider)");
        }
        ResourceConsumer previous = CONSUMERS.putIfAbsent(resourceId, consumer);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate resource consumer for '" + resourceId + "' - two owners for one currency");
        }
    }

    /**
     * Mod-gated declaration (issue #92): when {@code requiredModId} is not
     * loaded the declaration is <b>skipped with a debug line</b> - and the
     * pairing IAE does <b>not</b> fire (that rule targets genuine misuse, not
     * dependency absence: if the provider was gated by the same absent mod,
     * both halves are simply absent and downstream costs fail closed). Null
     * {@code requiredModId} is ungated.
     *
     * @param resourceId    the resource id
     * @param consumer      the write half
     * @param requiredModId the mod this resource's state comes from, or null for none
     */
    public static void declare(String resourceId, ResourceConsumer consumer,
            @Nullable String requiredModId) {
        if (requiredModId != null && !isModLoaded(requiredModId)) {
            LOGGER.debug("Resource consumer for '{}' skipped: required mod '{}' is not loaded",
                    resourceId, requiredModId);
            return;
        }
        declare(resourceId, consumer);
    }

    /**
     * Presence-resolved seam (package-private, tests): the gate without
     * touching ModList - fixtures pass the resolved boolean directly, so no
     * test ever boots FML.
     */
    static void declare(String resourceId, ResourceConsumer consumer, boolean requiredModPresent) {
        if (!requiredModPresent) {
            LOGGER.debug("Resource consumer for '{}' skipped: required mod is not loaded", resourceId);
            return;
        }
        declare(resourceId, consumer);
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    public static Optional<ResourceConsumer> get(String resourceId) {
        return Optional.ofNullable(resourceId == null ? null : CONSUMERS.get(resourceId));
    }

    public static boolean has(String resourceId) {
        return resourceId != null && CONSUMERS.containsKey(resourceId);
    }

    /**
     * Gives back a previously charged snapshot (issue #107): dispatches to the
     * charged resource's consumer (resolved from {@code snapshot.resourceId()}
     * - the same lookup the charge site uses) when it is a
     * {@link RefundableConsumer}, passing the <b>exact snapshot that was
     * charged</b> - never a recomputed amount, so charge and refund cannot
     * drift.
     *
     * <p>Fail-closed and never loud at the player: an absent consumer, a
     * consumer that is not refundable, or a refund that throws logs an error
     * and returns false (the throw is caught here - refund mirrors charge's
     * post-activation posture and never propagates). Ownership stays with the
     * pair's owner: integrators ask, they do not invert.</p>
     *
     * @param abilityId the ability that was charged
     * @param player    the player whose pool is restored
     * @param snapshot  the exact snapshot that was charged (names the resource)
     * @return true only when the owner's inverse ran
     * @throws IllegalArgumentException if the ability id or the snapshot is
     *         null (programmer misuse, loud like {@code declare})
     */
    public static boolean refund(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
        if (abilityId == null) {
            throw new IllegalArgumentException("abilityId must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "refund takes the exact charged snapshot, not null for " + abilityId);
        }
        String resourceId = snapshot.resourceId();
        ResourceConsumer consumer = CONSUMERS.get(resourceId);
        if (consumer == null) {
            LOGGER.error("Refund for '{}' refused: no consumer registered", resourceId);
            return false;
        }
        if (!(consumer instanceof RefundableConsumer refundable)) {
            LOGGER.error("Refund for '{}' refused: consumer is not refundable", resourceId);
            return false;
        }
        try {
            refundable.refund(abilityId, player, snapshot);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Refund for '{}' threw", resourceId, e);
            return false;
        }
    }

    /** Test isolation: removes a declared consumer. */
    public static boolean remove(String resourceId) {
        return resourceId != null && CONSUMERS.remove(resourceId) != null;
    }
}
