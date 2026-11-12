package org.bitwisemadness.abilitycore.mastery;

/**
 * The XP-to-level curve (REQ-CORE-12): level N costs
 * {@code base * growth^(N-1)} XP, accumulated until the pool runs out -
 * linear at {@code growth = 1}, geometric above. Levels are
 * <b>unbounded</b>: the effect asymptotes (see
 * {@code MasteryEffects.defaultMultiplier}) bound what mastery does, so
 * the level itself never needs a cap (an int saturates rather than
 * wraps at absurd pools). Pure math (params in, level out) so fixtures
 * pin it without a player or a config; callers read the params live
 * from {@code CoreConfig}.
 */
public final class MasteryCurve {

    private MasteryCurve() {
    }

    /**
     * The level an XP pool reaches (uncapped).
     *
     * @param xp     raw XP (non-positive yields 0)
     * @param base   XP for the level 0 -> 1 step (> 0)
     * @param growth per-step growth factor (> 0)
     * @return the level in {@code [0, Integer.MAX_VALUE]}
     */
    public static int level(double xp, double base, double growth) {
        if (!Double.isFinite(xp) || xp <= 0.0 || base <= 0.0 || growth <= 0.0) {
            return 0;
        }
        if (growth == 1.0) {
            // flat steps: closed form, so even absurd pools stay O(1)
            double flat = Math.floor(xp / base);
            return flat >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) flat;
        }
        double remaining = xp;
        int level = 0;
        while (true) {
            double step = base * Math.pow(growth, level);
            if (!Double.isFinite(step) || remaining < step) {
                break;
            }
            remaining -= step;
            if (level == Integer.MAX_VALUE) {
                break; // saturate, never wrap: the wire carries an int
            }
            level++;
        }
        return level;
    }

    /**
     * The pool's 0..1 progress inside its current level step - the
     * display's bar fill (REQ-CORE-12's row meter): where the pool sits
     * between this level's cumulative threshold and the next. The same
     * step accounting as {@link #level}, so the bar and the level can
     * never disagree; degenerate inputs answer 0 - never a guessed full
     * bar. With no level cap the formula path never pins at 1.0 (only a
     * datapack table's finite data does).
     *
     * @param xp     raw XP
     * @param base   XP for the level 0 -> 1 step (> 0)
     * @param growth per-step growth factor (> 0)
     * @return progress in {@code [0, 1)}
     */
    public static double progress(double xp, double base, double growth) {
        if (!Double.isFinite(xp) || xp <= 0.0 || base <= 0.0 || growth <= 0.0) {
            return 0.0;
        }
        if (growth == 1.0) {
            // flat steps: the pool's position inside its 0..base window
            double within = xp - Math.floor(xp / base) * base;
            return Math.max(0.0, Math.min(1.0, within / base));
        }
        double remaining = xp;
        int level = 0;
        double step = base;
        while (true) {
            if (!Double.isFinite(step) || remaining < step || level == Integer.MAX_VALUE) {
                break;
            }
            remaining -= step;
            level++;
            step = base * Math.pow(growth, level);
        }
        double fraction = remaining / step;
        return Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0;
    }
}
