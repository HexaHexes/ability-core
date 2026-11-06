package org.bitwisemadness.abilitycore.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Declared ability modes (issue #113, findings #112 recommendation 1): each
 * ability's static mode set, declared once at mod setup by its provider -
 * the {@link ConsumptionStrategies} registration pattern, chosen over a
 * definition component because modes are provider setup-time knowledge
 * (derived from upstream config), not datapack metadata (#112's rejected
 * alternative).
 *
 * <p>The provider derives each {@link Mode#nameKey()} itself: core never
 * encodes an upstream naming convention (leak-gate rule). The declaration
 * reaches clients through the synced declaration registry so menus and the
 * hotbar can read the cycle without importing the upstream mod.</p>
 *
 * <p>One declaration per ability id, loud ownership: duplicates fail, nulls
 * and blanks fail, and duplicate mode ids inside one declaration fail - a
 * repeated id makes the cycle ambiguous. Abilities with no modes simply do
 * not declare.</p>
 */
public final class AbilityModes {

    /**
     * One declared mode of an ability.
     *
     * @param id      stable per-ability mode identity (the cycle key stored
     *                in a hotbar slot - hotbar#29)
     * @param nameKey translation key for player-facing display; derived by
     *                the declaring provider
     */
    public record Mode(String id, String nameKey) {
        public Mode {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("mode id must not be blank");
            }
            if (nameKey == null || nameKey.isBlank()) {
                throw new IllegalArgumentException("mode name key must not be blank for " + id);
            }
        }
    }

    private static final Map<ResourceLocation, List<Mode>> DECLARED = new ConcurrentHashMap<>();

    private AbilityModes() {
    }

    /**
     * Declares one ability's mode cycle at mod construction (same timing
     * contract as strategies/owners - the registry is read afterwards).
     *
     * @throws IllegalArgumentException on null ability id, null/empty list,
     *         or null/blank/duplicated mode entries
     * @throws IllegalStateException    when the id is already declared (loud
     *         ownership conflict, never silent overwrite)
     */
    public static void declare(ResourceLocation abilityId, List<Mode> modes) {
        if (abilityId == null) {
            throw new IllegalArgumentException("ability id must not be null");
        }
        if (modes == null || modes.isEmpty()) {
            throw new IllegalArgumentException(
                    "declared modes must not be empty for " + abilityId + " - declare nothing instead");
        }
        List<Mode> copy = List.copyOf(modes); // rejects null elements
        for (Mode mode : copy) {
            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null for " + abilityId);
            }
        }
        for (int i = 0; i < copy.size(); i++) {
            for (int j = i + 1; j < copy.size(); j++) {
                if (copy.get(i).id().equals(copy.get(j).id())) {
                    throw new IllegalArgumentException("duplicate mode id '" + copy.get(i).id()
                            + "' declared for " + abilityId + " - the cycle would be ambiguous");
                }
            }
        }
        List<Mode> previous = DECLARED.putIfAbsent(abilityId, copy);
        if (previous != null) {
            throw new IllegalStateException("Duplicate mode declaration for " + abilityId
                    + " - one declaration per ability (issue #113)");
        }
    }

    /** The ability's declared mode cycle in order, if its provider declared one. */
    public static Optional<List<Mode>> get(ResourceLocation abilityId) {
        return Optional.ofNullable(abilityId == null ? null : DECLARED.get(abilityId));
    }

    /** Existence query for consumers deciding whether a cycle exists. */
    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && DECLARED.containsKey(abilityId);
    }

    /** Test isolation: removes a declaration (providers declare once per load). */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && DECLARED.remove(abilityId) != null;
    }
}
