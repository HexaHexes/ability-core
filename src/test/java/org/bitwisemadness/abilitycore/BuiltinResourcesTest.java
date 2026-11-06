package org.bitwisemadness.abilitycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The four built-in pairs (REQ-CORE-04, issue #88): registration at mod
 * construction declares provider+consumer for health, hunger, experience
 * points and experience levels, and the pure helpers carry the arithmetic -
 * fixtures need no ServerPlayer (core has no player fixture).
 */
class BuiltinResourcesTest {

    @AfterEach
    void clean() {
        for (String id : new String[] {
                BuiltinResources.HEALTH, BuiltinResources.HUNGER,
                BuiltinResources.EXPERIENCE_POINTS, BuiltinResources.EXPERIENCE_LEVELS,
                BuiltinResources.FREE }) {
            ResourceConsumers.remove(id);
            ResourceProviders.remove(id);
        }
    }

    @Test
    void registerAllDeclaresAllFivePairs() {
        BuiltinResources.registerAll();

        for (String id : new String[] {
                BuiltinResources.HEALTH, BuiltinResources.HUNGER,
                BuiltinResources.EXPERIENCE_POINTS, BuiltinResources.EXPERIENCE_LEVELS,
                BuiltinResources.FREE }) {
            assertTrue(ResourceProviders.has(id), "provider missing: " + id);
            assertTrue(ResourceConsumers.has(id), "consumer missing: " + id);
        }
        assertTrue(ResourceProviders.has("ability_core:experience_levels"),
                "ids are the reserved ability_core namespace");
    }

    @Test
    void poolCheckIsExactAtTheBoundary() {
        assertEquals(org.bitwisemadness.abilitycore.api.Affordability.State.SUFFICIENT,
                BuiltinResources.poolCheck("ability_core:health", 10.0, 10.0).state(),
                "a balance exactly equal to the cost is affordable");
        assertEquals(org.bitwisemadness.abilitycore.api.Affordability.State.SUFFICIENT,
                BuiltinResources.poolCheck("ability_core:health", 10.5, 10.0).state());
        assertEquals(org.bitwisemadness.abilitycore.api.Affordability.State.INSUFFICIENT,
                BuiltinResources.poolCheck("ability_core:health", 9.5, 10.0).state(),
                "one point short is not affordable");
        assertEquals(10.0,
                BuiltinResources.poolCheck("ability_core:health", 10.0, 10.0).amount(),
                "the snapshot carries the cost entry's amount, not the balance");
    }

    @Test
    void deductIntegralReturnsThePostDeductionLevel() {
        assertEquals(3, BuiltinResources.deductIntegral(5, 2), "5 - 2 = 3 (the level, not a delta)");
        assertEquals(0, BuiltinResources.deductIntegral(3, 2.5),
                "a fractional cost rounds up - the check approved balance >= 2.5");
        assertEquals(0, BuiltinResources.deductIntegral(1, 5),
                "clamped at zero - a pool never goes negative (defensive)");
    }

    @Test
    void deductedIntegralTakesTheRoundedAmountClampedToTheBalance() {
        // issue #108: the XP wiring needs the TAKEN delta, not the level
        assertEquals(2, BuiltinResources.deductedIntegral(5, 2), "plain take");
        assertEquals(3, BuiltinResources.deductedIntegral(3, 2.5),
                "fractional costs round up, but never past the balance");
        assertEquals(1, BuiltinResources.deductedIntegral(1, 5),
                "clamped at the balance - nothing negative is handed to giveExperience*");
        assertEquals(0, BuiltinResources.deductedIntegral(0, 2), "an empty pool takes nothing");
        assertEquals(0, BuiltinResources.deductedIntegral(5, 0), "a zero cost takes nothing");
    }

    @Test
    void theTwoHelpersSplitTheSubtractionIntoHalves() {
        // the property food's setFoodLevel already relies on, and the reason
        // the XP lines must use the delta helper: level == balance - taken
        double[][] cases = {{5, 2}, {15, 3}, {15, 2.5}, {1, 5}, {0, 2}};
        for (double[] c : cases) {
            assertEquals(BuiltinResources.deductIntegral(c[0], c[1]),
                    c[0] - BuiltinResources.deductedIntegral(c[0], c[1]),
                    "balance " + c[0] + " cost " + c[1] + ": post level == balance - taken");
        }
    }

    @Test
    void registerAllIsStrictAboutDoubleRegistration() {
        BuiltinResources.registerAll();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                BuiltinResources::registerAll,
                "construction runs once; a double call fails loudly, never silently overwrites");
    }

    @Test
    void freeAlwaysAnswersZeroAndChargesNothing() {
        // issue #93 AC1/AC2: the check ignores the requested amount (the truth
        // is always "nothing to consume") and the consumer is a verified no-op
        BuiltinResources.registerAll();

        var verdict = ResourceProviders.get(BuiltinResources.FREE).orElseThrow()
                .check(null, null, 999.0);
        assertEquals(org.bitwisemadness.abilitycore.api.Affordability.State.SUFFICIENT,
                verdict.state(), "free is always affordable");
        assertEquals(0.0, verdict.amount(),
                "the amount parameter is ignored - a free cost never claims consumption");

        try (var logs = org.bitwisemadness.abilitycore.LogCapture.forLogger(
                BuiltinResources.class, org.apache.logging.log4j.Level.DEBUG)) {
            ResourceConsumers.get(BuiltinResources.FREE).orElseThrow()
                    .charge(null, null, verdict);
            assertEquals(1, logs.messages().size(),
                    "the no-op charge is visible once in the debug log: " + logs.messages());
            assertTrue(logs.messages().get(0).contains("charged nothing"),
                    "the log states what it did: " + logs.messages().get(0));
        }
    }
}
