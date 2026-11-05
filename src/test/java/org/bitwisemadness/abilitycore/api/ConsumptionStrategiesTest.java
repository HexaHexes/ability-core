package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Consumption strategy registry and built-in selection functions
 * (issue #90). Selection is a pure function of (entries, consult answers,
 * RNG), so the built-ins are pinned here directly - pipeline integration
 * lives in {@code ConsumptionStrategyTest} (activation).
 */
class ConsumptionStrategiesTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strategy_probe");
    private static final ResourceCost A = new ResourceCost("test:a", 10.0);
    private static final ResourceCost B = new ResourceCost("test:b", 5.0);
    private static final ResourceCost C = new ResourceCost("test:c", 1.0);

    private static final ActivationRequest REQUEST = ActivationRequest.of(ABILITY, ActivationSource.DIRECT_SLOT);

    @AfterEach
    void resetSeam() {
        ConsumptionStrategies.setRandomForTests(null);
        ConsumptionStrategies.remove("test:custom");
    }

    private static List<ResourceCost> select(String name, Map<String, Affordability> verdicts) {
        return ConsumptionStrategies.get(name).orElseThrow()
                .select(REQUEST, null, List.of(A, B, C),
                        entry -> verdicts.get(entry.resourceId()));
    }

    /** A valid no-op strategy for declaration-guard fixtures. */
    private static ConsumptionStrategies.Strategy noop() {
        return (request, player, entries, check) -> entries;
    }

    private static Map<String, Affordability> allSufficient() {
        return Map.of(
                A.resourceId(), Affordability.sufficient(A.resourceId(), A.amount()),
                B.resourceId(), Affordability.sufficient(B.resourceId(), B.amount()),
                C.resourceId(), Affordability.sufficient(C.resourceId(), C.amount()));
    }

    @Test
    void builtinsArePreRegistered() {
        assertTrue(ConsumptionStrategies.has(ConsumptionStrategies.ALL));
        assertTrue(ConsumptionStrategies.has(ConsumptionStrategies.RANDOM));
        assertTrue(ConsumptionStrategies.has(ConsumptionStrategies.IN_ORDER));
    }

    @Test
    void declareEnforcesLoudOwnership() {
        assertThrows(IllegalArgumentException.class, () -> ConsumptionStrategies.declare(null, noop()));
        assertThrows(IllegalArgumentException.class, () -> ConsumptionStrategies.declare("  ", noop()));
        assertThrows(IllegalArgumentException.class,
                () -> ConsumptionStrategies.declare("test:custom", null));
        ConsumptionStrategies.declare("test:custom", (request, player, entries, check) -> List.of());
        assertThrows(IllegalStateException.class,
                () -> ConsumptionStrategies.declare("test:custom", noop()),
                "two owners for one strategy name fails loudly, never overwrites");
        assertTrue(ConsumptionStrategies.remove("test:custom"), "test isolation");
        assertFalse(ConsumptionStrategies.has("test:custom"));
        assertFalse(ConsumptionStrategies.has(null));
        assertFalse(ConsumptionStrategies.get("definitely_absent").isPresent());
    }

    @Test
    void allSelectsEveryEntryInListOrder() {
        var selected = select(ConsumptionStrategies.ALL, allSufficient());
        assertEquals(List.of(A, B, C), selected, "all never consults and never reorders");
        // and with nothing affordable: still every entry - validation decides
        assertEquals(List.of(A, B, C),
                select(ConsumptionStrategies.ALL, Map.of()),
                "all selects structurally; affordability is core's validation pass");
    }

    @Test
    void randomPicksOnlyAmongAffordableUnderInjectedRng() {
        var verdicts = Map.of(
                A.resourceId(), Affordability.insufficient(A.resourceId(), A.amount()),
                B.resourceId(), Affordability.sufficient(B.resourceId(), B.amount()),
                C.resourceId(), Affordability.sufficient(C.resourceId(), C.amount()));

        // scripted RNG: index 0 of [B, C] -> B; index 1 -> C
        ConsumptionStrategies.setRandomForTests(new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        assertEquals(List.of(B), select(ConsumptionStrategies.RANDOM, verdicts),
                "insufficient A is never a candidate");

        ConsumptionStrategies.setRandomForTests(new Random() {
            @Override
            public int nextInt(int bound) {
                return 1;
            }
        });
        assertEquals(List.of(C), select(ConsumptionStrategies.RANDOM, verdicts),
                "the same consult answers, a different roll - deterministic under the seam");
    }

    @Test
    void randomIsEmptyWhenNothingIsAffordable() {
        assertEquals(List.of(), select(ConsumptionStrategies.RANDOM, Map.of()),
                "no affordable entry = empty selection = pipeline refusal");
        var unknownOnly = Map.of(A.resourceId(), Affordability.unknown(A.resourceId()),
                B.resourceId(), Affordability.unknown(B.resourceId()),
                C.resourceId(), Affordability.unknown(C.resourceId()));
        assertEquals(List.of(), select(ConsumptionStrategies.RANDOM, unknownOnly),
                "UNKNOWN reads as not affordable (fail closed)");
    }

    @Test
    void inOrderFullPassSelectsAllInListOrder() {
        assertEquals(List.of(A, B, C), select(ConsumptionStrategies.IN_ORDER, allSufficient()),
                "every entry covered -> consume all, in list order");
    }

    @Test
    void inOrderAbortsAtTheFirstUncoveredEntry() {
        // recorded verbatim: "Consume from each in order, till the cost is
        // satisfied; if cost can not be satisfied, fail before consuming"
        var consulted = new java.util.ArrayList<String>();
        var verdicts = Map.of(
                A.resourceId(), Affordability.sufficient(A.resourceId(), A.amount()),
                B.resourceId(), Affordability.insufficient(B.resourceId(), B.amount()),
                C.resourceId(), Affordability.sufficient(C.resourceId(), C.amount()));

        var selected = ConsumptionStrategies.get(ConsumptionStrategies.IN_ORDER).orElseThrow()
                .select(REQUEST, null, List.of(A, B, C), entry -> {
                    consulted.add(entry.resourceId());
                    return verdicts.get(entry.resourceId());
                });

        assertEquals(List.of(), selected,
                "abort at B -> empty selection -> refusal; A is NOT consumed partially");
        assertEquals(List.of(A.resourceId(), B.resourceId()), consulted,
                "consults stop at the abort point - C is never touched");
    }
}
