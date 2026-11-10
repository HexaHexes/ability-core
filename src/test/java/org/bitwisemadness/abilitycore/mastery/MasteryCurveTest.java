package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
