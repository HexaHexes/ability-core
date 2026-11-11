package org.bitwisemadness.abilitycore.mastery;

/**
 * The XP-to-level curve (REQ-CORE-12): level N costs
 * {@code base × growth^(N-1)} XP, accumulated until the pool runs out -
 * linear at {@code growth = 1}, geometric above, capped at
 * {@code maxLevel}. Pure math (params in, level out) so fixtures pin it
 * without a player or a config; callers read the params live from
 * {@code CoreConfig}.
 */
public final class MasteryCurve {

    private MasteryCurve() {
    }

    /**
     * The level an XP pool reaches.
     *
     * @param xp        raw XP (non-positive yields 0)
     * @param maxLevel  the cap (levels stop here)
     * @param base      XP for the level 0 -> 1 step (>= 1)
     * @param growth    per-step growth factor (>= 1)
     * @return the level in {@code [0, maxLevel]}
     */
    public static int level(double xp, int maxLevel, double base, double growth) {
        if (!Double.isFinite(xp) || xp <= 0.0 || maxLevel <= 0) {
            return 0;
        }
        double remaining = xp;
        int level = 0;
        while (level < maxLevel) {
            double step = base * Math.pow(growth, level);
            if (!Double.isFinite(step) || remaining < step) {
                break;
            }
            remaining -= step;
            level++;
        }
        return level;
    }

    /**
     * The pool's 0..1 progress inside its current level step - the
     * display's bar fill (REQ-CORE-12's row meter): where the pool sits
     * between this level's cumulative threshold and the next, pinned at
     * 1.0 once the cap's steps are all covered. The same step accounting
     * as {@link #level}, so the bar and the level can never disagree;
     * degenerate inputs answer 0 - never a guessed full bar.
     *
     * @param xp        raw XP
     * @param maxLevel  the cap (progress pins at 1.0 there)
     * @param base      XP for the level 0 -> 1 step (>= 1)
     * @param growth    per-step growth factor (>= 1)
     * @return progress in {@code [0, 1]}
     */
    public static double progress(double xp, int maxLevel, double base, double growth) {
        if (!Double.isFinite(xp) || xp <= 0.0 || maxLevel <= 0 || base <= 0.0) {
            return 0.0;
        }
        double remaining = xp;
        int level = 0;
        double step = base;
        while (level < maxLevel) {
            if (!Double.isFinite(step) || remaining < step) {
                break;
            }
            remaining -= step;
            level++;
            step = base * Math.pow(growth, level);
        }
        if (level >= maxLevel) {
            return 1.0;
        }
        double fraction = remaining / step;
        return Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0;
    }
}
