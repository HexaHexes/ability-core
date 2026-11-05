package org.bitwisemadness.abilitycore.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Declared resource consumers, one per resource id (REQ-CORE-04).
 *
 * <p>Resources are declared by their owner (battlewills declares aura, a
 * provider declares its own pool) - duplicate declaration for one resource id
 * fails loudly: two owners for one currency is exactly the ambiguity that
 * produces double-charging.</p>
 */
public final class ResourceConsumers {

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
