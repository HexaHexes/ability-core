package org.bitwisemadness.tensuraabilitycore.activation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-ability activation handlers (REQ-CORE-02's ADAPTER presence check).
 *
 * <p>Providers register during their mod setup (FMLCommonSetup enqueueWork);
 * the registry is instance-based so tests (and future sub-registries) stay
 * isolated - the pipeline takes its registry explicitly. Duplicate
 * registration for the same id fails loudly (two adapters claiming one
 * ability is exactly the conflict class the brief forbids).</p>
 */
public final class ActivationHandlerRegistry {

    private final Map<ResourceLocation, ActivationHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Registers the provider adapter for one ability id.
     *
     * @throws IllegalStateException if an adapter is already registered (loud
     *         conflict, never silent overwrite)
     */
    public void register(ResourceLocation abilityId, ActivationHandler handler) {
        ActivationHandler previous = handlers.putIfAbsent(abilityId, handler);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate activation handler for ability " + abilityId + " - two adapters claim one ability");
        }
    }

    public Optional<ActivationHandler> get(ResourceLocation abilityId) {
        return Optional.ofNullable(handlers.get(abilityId));
    }

    public boolean has(ResourceLocation abilityId) {
        return handlers.containsKey(abilityId);
    }

    public int size() {
        return handlers.size();
    }

    /**
     * Removes the handler for an id (test isolation and future mod-reload
     * paths; providers register once per load, so normal flows never call this).
     *
     * @return true if a handler was present
     */
    public boolean remove(ResourceLocation abilityId) {
        return handlers.remove(abilityId) != null;
    }
}
