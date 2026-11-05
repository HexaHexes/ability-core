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
    void deductIntegralRoundsUpAndNeverGoesNegative() {
        assertEquals(3, BuiltinResources.deductIntegral(5, 2), "plain deduction");
        assertEquals(0, BuiltinResources.deductIntegral(3, 2.5),
                "a fractional cost rounds up - the check approved balance >= 2.5");
        assertEquals(0, BuiltinResources.deductIntegral(1, 5),
                "clamped at zero - a pool never goes negative (defensive)");
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
