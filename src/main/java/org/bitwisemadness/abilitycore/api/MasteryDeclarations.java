package org.bitwisemadness.abilitycore.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * The opt-in mastery registry (REQ-CORE-12): one {@link MasterySpec} per
 * ability id, declared at mod construction (the same timing contract as
 * providers, consumers and strategies - definitions register at
 * {@code RegisterEvent}, after all mods are constructed, so declaration
 * order between mods is free). An id with no declaration never touches
 * the system: no storage, no awards, no effect reads.
 *
 * <p>Handler names inside a spec resolve at first use (award or effect
 * read), never at declaration - that keeps declaration order between
 * {@link MasteryGainHandlers} and this registry irrelevant. An unknown
 * name logs an error at use and falls back loudly (base gain / default
 * curve) instead of failing an activation.</p>
 */
public final class MasteryDeclarations {

    private static final Map<ResourceLocation, MasterySpec> SPECS = new ConcurrentHashMap<>();

    private MasteryDeclarations() {
    }

    /**
     * Declares the mastery spec for one ability id.
     *
     * @param abilityId the ability this contract belongs to
     * @param spec      the opt-in contract
     * @throws IllegalArgumentException on a null id or spec
     * @throws IllegalStateException    if the id already has a spec (loud
     *         ownership conflict, never silent overwrite)
     */
    public static void declare(ResourceLocation abilityId, MasterySpec spec) {
        if (abilityId == null) {
            throw new IllegalArgumentException("abilityId must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null for " + abilityId);
        }
        MasterySpec previous = SPECS.putIfAbsent(abilityId, spec);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate mastery declaration for " + abilityId + " - two owners claim one ability");
        }
    }

    /** @return the spec for an id; empty when the ability opted out */
    public static Optional<MasterySpec> get(ResourceLocation abilityId) {
        return Optional.ofNullable(abilityId == null ? null : SPECS.get(abilityId));
    }

    /** Existence query for the pipeline and effect reads (never guesses). */
    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && SPECS.containsKey(abilityId);
    }

    /** Test isolation: removes a declaration (returns whether one existed). */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && SPECS.remove(abilityId) != null;
    }
}
