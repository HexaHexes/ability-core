package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The default effect curves (REQ-CORE-12): an asymptotic approach to
 * the configured bound - reductions are {@code 1 - approach} and bonuses
 * {@code 1 + approach} with {@code approach = max * (1 - e^(-rate *
 * level))} - so each multiplier nears its X/Y/Z/A (the max* config keys)
 * as the level grows without bound and never crosses it. Level 0 is
 * always the inert 1.0. Values assert against the readers' shipped
 * defaults (0.005/0.25 reductions, 0.01/0.5 bonuses) with the config
 * unloaded - exactly the fallback path production uses before a config
 * file exists.
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
    void reductionsNearTheirAsymptote() {
        // default rate 0.005, asymptote 0.25: 1 - 0.25*(1 - e^(-0.005*L))
        assertEquals(1.0 - 0.25 * (1.0 - Math.exp(-0.005 * 10.0)),
                MasteryEffects.defaultMultiplier(MasteryAxis.COST, 10.0), 1e-9);
        assertEquals(1.0 - 0.25 * (1.0 - Math.exp(-0.005 * 1.0)),
                MasteryEffects.defaultMultiplier(MasteryAxis.CAST_TIME, 1.0), 1e-9);
        // the owner's X/Z: nears 0.75 (a cost never below 75%) but never crosses
        assertEquals(0.75, MasteryEffects.defaultMultiplier(MasteryAxis.COST, 1.0e6), 1e-9,
                "at a vast level the reduction sits on its asymptote");
        double atFinite = MasteryEffects.defaultMultiplier(MasteryAxis.COST, 1_000.0);
        assertTrue(atFinite > 0.75 && atFinite < 1.0,
                "a finite level approaches without reaching, got " + atFinite);
    }

    @Test
    void bonusesNearTheirAsymptote() {
        // default rate 0.01, asymptote 0.5: 1 + 0.5*(1 - e^(-0.01*L))
        assertEquals(1.0 + 0.5 * (1.0 - Math.exp(-0.01 * 10.0)),
                MasteryEffects.defaultMultiplier(MasteryAxis.POWER, 10.0), 1e-9);
        assertEquals(1.0 + 0.5 * (1.0 - Math.exp(-0.01 * 1.0)),
                MasteryEffects.defaultMultiplier(MasteryAxis.DURATION, 1.0), 1e-9);
        // the owner's Y/A: nears 1.5 (+50%) but never crosses
        assertEquals(1.5, MasteryEffects.defaultMultiplier(MasteryAxis.POWER, 1.0e6), 1e-9);
        double atFinite = MasteryEffects.defaultMultiplier(MasteryAxis.POWER, 1_000.0);
        assertTrue(atFinite > 1.0 && atFinite < 1.5,
                "a finite level approaches without reaching, got " + atFinite);
    }

    @Test
    void curvesAreMonotoneTowardTheirBounds() {
        double previousReduction = 1.0;
        for (int level = 0; level <= 500; level += 25) {
            double reduction = MasteryEffects.defaultMultiplier(MasteryAxis.COST,
                    (double) level);
            assertTrue(reduction <= previousReduction + 1e-12,
                    "reductions never regress at level " + level);
            assertTrue(reduction >= 0.75 - 1e-12,
                    "reductions never cross the asymptote at level " + level);
            previousReduction = reduction;
        }
        double previousBonus = 1.0;
        for (int level = 0; level <= 500; level += 25) {
            double bonus = MasteryEffects.defaultMultiplier(MasteryAxis.POWER,
                    (double) level);
            assertTrue(bonus >= previousBonus - 1e-12,
                    "bonuses never regress at level " + level);
            assertTrue(bonus <= 1.5 + 1e-12,
                    "bonuses never cross the asymptote at level " + level);
            previousBonus = bonus;
        }
    }

    @Test
    void zeroRateMeansNoEffectEver() {
        // a pack that turns a rate off gets the inert curve (approach = 0)
        assertEquals(0.0, MasteryEffects.approach(0.25, 0.0, 1_000.0), 1e-12,
                "a zero asymptote applies nothing");
        assertEquals(0.0, MasteryEffects.approach(0.25, 0.005, 0.0), 1e-12,
                "level 0 applies nothing");
    }
}
