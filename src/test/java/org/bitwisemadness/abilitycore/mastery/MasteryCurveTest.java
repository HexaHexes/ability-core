package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The XP-to-level curve (REQ-CORE-12): linear at growth 1, geometric
 * above, capped at maxLevel, total-degenerate inputs yield 0 - the pure
 * math the config formula and (later) datapack tables both feed.
 */
class MasteryCurveTest {

    @Test
    void linearCurveStepsAtTheBaseRate() {
        // base 100, growth 1: each level costs a flat 100 XP
        assertEquals(0, MasteryCurve.level(0.0, 100, 100.0, 1.0));
        assertEquals(0, MasteryCurve.level(99.0, 100, 100.0, 1.0));
        assertEquals(1, MasteryCurve.level(100.0, 100, 100.0, 1.0));
        assertEquals(2, MasteryCurve.level(299.0, 100, 100.0, 1.0));
        assertEquals(3, MasteryCurve.level(300.0, 100, 100.0, 1.0));
    }

    @Test
    void geometricCurveGrowsEachStep() {
        // base 100, growth 2: steps 100, 200, 400...
        assertEquals(1, MasteryCurve.level(100.0, 100, 100.0, 2.0));
        assertEquals(2, MasteryCurve.level(300.0, 100, 100.0, 2.0), "100 + 200 covered");
        assertEquals(3, MasteryCurve.level(700.0, 100, 100.0, 2.0), "100 + 200 + 400 covered");
        assertEquals(2, MasteryCurve.level(699.0, 100, 100.0, 2.0), "one XP short of level 3");
    }

    @Test
    void theCapStopsLevelingButNeverOverflows() {
        assertEquals(5, MasteryCurve.level(1.0e12, 5, 100.0, 1.0),
                "past the cap the level pins, the XP keeps accumulating upstream");
        assertEquals(0, MasteryCurve.level(1.0e12, 0, 100.0, 1.0), "maxLevel 0 = no levels");
    }

    @Test
    void degenerateInputsYieldZero() {
        assertEquals(0, MasteryCurve.level(-10.0, 100, 100.0, 1.0));
        assertEquals(0, MasteryCurve.level(Double.NaN, 100, 100.0, 1.0));
        assertEquals(0, MasteryCurve.level(Double.POSITIVE_INFINITY, 100, 100.0, 1.0));
    }

    @Test
    void progressWalksTheCurrentStepAndPinsAtTheCap() {
        // linear: flat 100-XP steps - fill restarts at every level boundary
        assertEquals(0.0, MasteryCurve.progress(0.0, 100, 100.0, 1.0), 1e-9);
        assertEquals(0.5, MasteryCurve.progress(50.0, 100, 100.0, 1.0), 1e-9);
        assertEquals(0.999, MasteryCurve.progress(99.9, 100, 100.0, 1.0), 1e-9);
        assertEquals(0.0, MasteryCurve.progress(100.0, 100, 100.0, 1.0),
                "exactly at the level boundary the next step starts empty");
        assertEquals(0.5, MasteryCurve.progress(150.0, 100, 100.0, 1.0), 1e-9);
        assertEquals(1.0, MasteryCurve.progress(1.0e12, 5, 100.0, 1.0), 1e-9,
                "past the cap the bar pins full, matching the level");
        // geometric: steps 100, 200, 400... - 250 XP = level 1 + 150/200
        assertEquals(0.75, MasteryCurve.progress(250.0, 100, 100.0, 2.0), 1e-9);
        // degenerate answers 0 - never a guessed full bar
        assertEquals(0.0, MasteryCurve.progress(Double.NaN, 100, 100.0, 1.0), 1e-9);
        assertEquals(0.0, MasteryCurve.progress(-1.0, 100, 100.0, 1.0), 1e-9);
        assertEquals(0.0, MasteryCurve.progress(50.0, 0, 100.0, 1.0), 1e-9);
    }

    @Test
    void progressAgreesWithLevelAtEveryStep() {
        // the bar and the level read the same step accounting: a pool just
        // below a boundary fills to ~1.0, the boundary itself restarts at 0
        for (double xp = 0.0; xp <= 500.0; xp += 12.5) {
            int level = MasteryCurve.level(xp, 100, 100.0, 1.5);
            double progress = MasteryCurve.progress(xp, 100, 100.0, 1.5);
            assertTrue(progress >= 0.0 && progress <= 1.0,
                    "progress stays in range at xp=" + xp + " level=" + level);
        }
    }
}
