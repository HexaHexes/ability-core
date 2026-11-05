package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Declared resource providers, one per resource id (REQ-CORE-04, issue #88).
 *
 * <p>The read half of the pair: a provider registers how its resource is
 * <em>accessed</em> (the affordability check the pipeline runs). Rules mirror
 * {@link ResourceConsumers}: one provider per id, duplicate declaration fails
 * loudly - two providers for one currency is the ambiguity that produces
 * contradictory verdicts. The paired consumer always requires its provider
 * (declared first), so a resource can never be charged without a declared
 * way to validate it.</p>
 *
 * <p>Declared at mod construction - ability definitions register at
 * {@code RegisterEvent}, which fires after all mods are constructed, so
 * registration-time cost decisions (e.g. "XP unless aura is registered")
 * are order-safe.</p>
 */
public final class ResourceProviders {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, ResourceProvider> PROVIDERS = new ConcurrentHashMap<>();

    private ResourceProviders() {
    }

    /**
     * Declares the provider for one resource id.
     *
     * @throws IllegalArgumentException if the resource id is null/blank or the provider is null
     * @throws IllegalStateException    if the resource id already has a provider
     *         (loud ownership conflict, never silent overwrite)
     */
    public static void declare(String resourceId, ResourceProvider provider) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null for " + resourceId);
        }
        ResourceProvider previous = PROVIDERS.putIfAbsent(resourceId, provider);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate resource provider for '" + resourceId + "' - two owners for one currency");
        }
    }

    /**
     * Mod-gated declaration (issue #92): when {@code requiredModId} is not
     * loaded the declaration is <b>skipped with a debug line</b> - absence is
     * expected, not misuse - and {@link #has} reads false downstream, so
     * abilities costing this resource fail closed with
     * {@code ability.refused.unaffordable.no_provider}: the auto-disable
     * chain. Null {@code requiredModId} is ungated (core's built-ins).
     * Call at mod construction - ModList is populated before construction.
     *
     * @param resourceId    the resource id
     * @param provider      the read half
     * @param requiredModId the mod this resource's state comes from, or null for none
     */
    public static void declare(String resourceId, ResourceProvider provider,
            @Nullable String requiredModId) {
        declare(resourceId, provider, requiredModId == null || isModLoaded(requiredModId));
    }

    /**
     * Presence-resolved seam (package-private, tests): the gate without
     * touching ModList - fixtures pass the resolved boolean directly, so no
     * test ever boots FML.
     */
    static void declare(String resourceId, ResourceProvider provider, boolean requiredModPresent) {
        if (!requiredModPresent) {
            LOGGER.debug("Resource provider for '{}' skipped: required mod is not loaded", resourceId);
            return;
        }
        declare(resourceId, provider);
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    public static Optional<ResourceProvider> get(String resourceId) {
        return Optional.ofNullable(resourceId == null ? null : PROVIDERS.get(resourceId));
    }

    /** Existence query for conditional cost selection (issue #88, pseudocore#174). */
    public static boolean has(String resourceId) {
        return resourceId != null && PROVIDERS.containsKey(resourceId);
    }

    /** Test isolation: removes a declared provider. */
    public static boolean remove(String resourceId) {
        return resourceId != null && PROVIDERS.remove(resourceId) != null;
    }
}
