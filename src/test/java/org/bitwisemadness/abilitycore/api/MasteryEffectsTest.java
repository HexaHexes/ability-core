package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The default effect curves (REQ-CORE-12): reductions are
 * {@code 1 - min(max, perLevel x level)} clamped by the config ranges to
 * {@code (0, 1]}, bonuses are {@code 1 + min(max, perLevel x level)} -
 * level 0 is always the inert 1.0. Values assert against the readers'
 * shipped defaults (0.005/0.25 reductions, 0.01/0.5 bonuses) with the
 * config unloaded, which is exactly the fallback path production uses
 * before a config file exists.
 */
class MasteryEffectsTest {

    @Test
    void levelZeroIsAlwaysInert() {
        for (MasteryAxis axis : MasteryAxis.values()) {
            assertEquals(1.0, MasteryEffects.defaultMultiplier(axis, 0.0), 1e-9,
                    axis + " at level 0 applies nothing");
        }
        assertEquals(1.0, MasteryEffects.defaultMultiplier(MasteryAxis.COST, -5.0), 1e-9,
                "a negative level clamps to inert, never to a bonus");
    }

    @Test
    void reductionsScaleThenCap() {
        // default 0.005/level, cap 0.25
        assertEquals(0.95, MasteryEffects.defaultMultiplier(MasteryAxis.COST, 10.0), 1e-9);
        assertEquals(0.995, MasteryEffects.defaultMultiplier(MasteryAxis.CAST_TIME, 1.0), 1e-9);
        // 1000 levels x 0.005 = 5.0 -> capped at 0.25 off
        assertEquals(0.75, MasteryEffects.defaultMultiplier(MasteryAxis.COST, 1000.0), 1e-9);
        assertEquals(0.75, MasteryEffects.defaultMultiplier(MasteryAxis.CAST_TIME, 1000.0), 1e-9);
    }

    @Test
    void bonusesScaleThenCap() {
        // default 0.01/level, cap 0.5
        assertEquals(1.10, MasteryEffects.defaultMultiplier(MasteryAxis.POWER, 10.0), 1e-9);
        assertEquals(1.01, MasteryEffects.defaultMultiplier(MasteryAxis.DURATION, 1.0), 1e-9);
        assertEquals(1.50, MasteryEffects.defaultMultiplier(MasteryAxis.POWER, 1000.0), 1e-9);
        assertEquals(1.50, MasteryEffects.defaultMultiplier(MasteryAxis.DURATION, 1000.0), 1e-9);
    }
}
