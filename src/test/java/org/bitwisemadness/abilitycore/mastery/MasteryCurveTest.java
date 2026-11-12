package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The XP-to-level curve (REQ-CORE-12): linear at growth 1, geometric
 * above, UNBOUNDED (the effect asymptotes bound what mastery does - the
 * int saturates rather than wrapping), and degenerate inputs yield 0 -
 * the pure math the config formula feeds.
 */
class MasteryCurveTest {

    @Test
    void linearCurveStepsAtTheBaseRate() {
        // base 100, growth 1: each level costs a flat 100 XP
        assertEquals(0, MasteryCurve.level(0.0, 100.0, 1.0));
        assertEquals(0, MasteryCurve.level(99.0, 100.0, 1.0));
        assertEquals(1, MasteryCurve.level(100.0, 100.0, 1.0));
        assertEquals(2, MasteryCurve.level(299.0, 100.0, 1.0));
        assertEquals(3, MasteryCurve.level(300.0, 100.0, 1.0));
    }

    @Test
    void geometricCurveGrowsEachStep() {
        // base 100, growth 2: steps 100, 200, 400...
        assertEquals(1, MasteryCurve.level(100.0, 100.0, 2.0));
        assertEquals(2, MasteryCurve.level(300.0, 100.0, 2.0), "100 + 200 covered");
        assertEquals(3, MasteryCurve.level(700.0, 100.0, 2.0), "100 + 200 + 400 covered");
        assertEquals(2, MasteryCurve.level(699.0, 100.0, 2.0), "one XP short of level 3");
    }

    @Test
    void levelsAreUnboundedAndSaturateInsteadOfWrapping() {
        // no cap: past the old 100 ceiling the level keeps counting...
        assertEquals(100_000, MasteryCurve.level(10_000_000.0, 100.0, 1.0),
                "flat steps keep converting XP past the old cap");
        assertTrue(MasteryCurve.level(1.0e15, 100.0, 1.15) > 100,
                "geometric steps keep counting past the old cap too");
        // ...and an absurd pool saturates the int rather than wrapping
        assertEquals(Integer.MAX_VALUE, MasteryCurve.level(1.0e30, 1.0, 1.0),
                "the wire carries an int: saturate, never wrap (O(1) closed form)");
    }

    @Test
    void degenerateInputsYieldZero() {
        assertEquals(0, MasteryCurve.level(-10.0, 100.0, 1.0));
        assertEquals(0, MasteryCurve.level(Double.NaN, 100.0, 1.0));
        assertEquals(0, MasteryCurve.level(Double.POSITIVE_INFINITY, 100.0, 1.0));
        assertEquals(0, MasteryCurve.level(100.0, 0.0, 1.0), "base must be positive");
        assertEquals(0, MasteryCurve.level(100.0, 100.0, 0.0), "growth must be positive");
    }

    @Test
    void progressWalksTheCurrentStepAndNeverPins() {
        // linear: flat 100-XP steps - fill restarts at every level boundary
        assertEquals(0.0, MasteryCurve.progress(0.0, 100.0, 1.0), 1e-9);
        assertEquals(0.5, MasteryCurve.progress(50.0, 100.0, 1.0), 1e-9);
        assertEquals(0.999, MasteryCurve.progress(99.9, 100.0, 1.0), 1e-9);
        assertEquals(0.0, MasteryCurve.progress(100.0, 100.0, 1.0), 1e-9,
                "exactly at the level boundary the next step starts empty");
        assertEquals(0.5, MasteryCurve.progress(150.0, 100.0, 1.0), 1e-9);
        // geometric: steps 100, 200, 400... - 250 XP = level 1 + 150/200
        assertEquals(0.75, MasteryCurve.progress(250.0, 100.0, 2.0), 1e-9);
        // unbounded: even a colossal pool sits INSIDE some step - never a pin
        double vast = MasteryCurve.progress(1.0e15, 100.0, 1.15);
        assertTrue(vast >= 0.0 && vast < 1.0,
                "the unbounded formula fill stays inside [0,1), got " + vast);
        // degenerate answers 0 - never a guessed full bar
        assertEquals(0.0, MasteryCurve.progress(Double.NaN, 100.0, 1.0), 1e-9);
        assertEquals(0.0, MasteryCurve.progress(-1.0, 100.0, 1.0), 1e-9);
        assertEquals(0.0, MasteryCurve.progress(50.0, 0.0, 1.0), 1e-9);
        assertEquals(0.0, MasteryCurve.progress(50.0, 100.0, 0.0), 1e-9);
    }

    @Test
    void progressAndLevelAlwaysAgree() {
        // the bar and the level read the same step accounting: whenever
        // the level advances, the fill restarts near zero
        for (double xp = 0.0; xp <= 2_000.0; xp += 7.5) {
            int level = MasteryCurve.level(xp, 100.0, 1.15);
            double progress = MasteryCurve.progress(xp, 100.0, 1.15);
            assertTrue(progress >= 0.0 && progress < 1.0,
                    "in-step fill at xp=" + xp + " level=" + level + " got " + progress);
            if (xp > 0 && level > 0) {
                double beforeBoundary = MasteryCurve.progress(xp - 1.0, 100.0, 1.15);
                if (level > MasteryCurve.level(xp - 1.0, 100.0, 1.15)) {
                    assertTrue(progress < beforeBoundary,
                            "a level-up restarts the fill at xp=" + xp);
                }
            }
        }
    }
}
