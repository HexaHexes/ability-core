package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Test isolation: removes a declared consumer. */
    public static boolean remove(String resourceId) {
        return resourceId != null && CONSUMERS.remove(resourceId) != null;
    }
}
