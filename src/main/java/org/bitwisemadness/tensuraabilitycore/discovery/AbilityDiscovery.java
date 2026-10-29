package org.bitwisemadness.tensuraabilitycore.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tensuraabilitycore.TensuraAbilityCore;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Provider-independent discovery over the core registry (REQ-CORE-03).
 *
 * <p>Consumers never branch on {@code ModList.isLoaded} for ability data:
 * provider presence IS registration presence. A saved reference to an id whose
 * provider is absent resolves to {@link PresenceState#INERT_MISSING} - the
 * consumer renders a diagnosed inert slot and preserves the raw id (REQ-HOT-15/
 * REQ-HOT-21 semantics live in the consumer; this class only reports truth).</p>
 *
 * <p>Login-window lifecycle: before the registry exists, {@link #isReady()}
 * is false and presence reports {@link PresenceState#NOT_READY} - consumers
 * render a placeholder until first sync instead of flashing empty (core#19's
 * snapshot contract consumes this).</p>
 *
 * <p>No {@code ModList} usage anywhere in this class (issue #14's grep-gate
 * acceptance is structurally satisfied; the JUnit leak gate covers types).</p>
 */
public final class AbilityDiscovery {

    private AbilityDiscovery() {
    }

    /** What a consumer should do with a saved/queried id right now. */
    public enum PresenceState {
        /** Registry not constructed yet (pre-load / pre-sync window). */
        NOT_READY,
        /** Id not registered - preserve raw id, render diagnosed inert slot. */
        INERT_MISSING,
        /** Definition present; consult handler presence for activationability. */
        AVAILABLE
    }

    /** False until NewRegistryEvent constructed the registry. */
    public static boolean isReady() {
        return AbilityRegistries.registry() != null;
    }

    public static PresenceState presence(ResourceLocation abilityId) {
        var definitions = AbilityRegistries.registry();
        if (definitions == null) {
            return PresenceState.NOT_READY;
        }
        return definitions.containsKey(abilityId)
                ? PresenceState.AVAILABLE
                : PresenceState.INERT_MISSING;
    }

    public static boolean isRegistered(ResourceLocation abilityId) {
        var definitions = AbilityRegistries.registry();
        return definitions != null && definitions.containsKey(abilityId);
    }

    /**
     * Registered AND has an activation handler - i.e. an activation attempt
     * would get past the pipeline's built-in RESOLVE/ADAPTER gates.
     */
    public static boolean isResolvable(ResourceLocation abilityId) {
        return isRegistered(abilityId) && TensuraAbilityCore.HANDLERS.has(abilityId);
    }

    public static Optional<AbilityDefinition> get(ResourceLocation abilityId) {
        var definitions = AbilityRegistries.registry();
        if (definitions == null || !definitions.containsKey(abilityId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(abilityId));
    }

    /** Every definition currently registered (empty pre-ready; never null). */
    public static List<AbilityDefinition> all() {
        var definitions = AbilityRegistries.registry();
        if (definitions == null) {
            return List.of();
        }
        List<AbilityDefinition> out = new ArrayList<>();
        definitions.forEach(out::add);
        return List.copyOf(out);
    }

    /** Definitions of one open category (e.g. {@code tensura:battlewill}). */
    public static List<AbilityDefinition> byCategory(ResourceLocation category) {
        return all().stream().filter(d -> d.category().equals(category)).toList();
    }

    /**
     * Definitions carrying the given tag in their open {@code tags} set.
     * Set-based on purpose: vanilla {@code TagKey} support for modded built-in
     * registries is unverified on 1.21.1 (issue #12 PR note 1).
     */
    public static List<AbilityDefinition> byTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        return all().stream().filter(d -> d.tags().contains(tag)).toList();
    }

    /** Convenience for diagnostics: state + definition in one call. */
    public static Optional<AbilityDefinition> getIfAvailable(@Nullable ResourceLocation abilityId) {
        if (abilityId == null) {
            return Optional.empty();
        }
        return get(abilityId);
    }
}
