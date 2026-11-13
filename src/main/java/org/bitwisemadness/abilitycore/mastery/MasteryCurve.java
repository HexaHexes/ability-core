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

    /**
     * The inverse of {@link #level}: the minimum XP that reaches the
     * given level - the admin command's set/add conversion (the same
     * step accounting, so a command's "5" is the level the pane shows).
     * The log/pow chain compounds rounding and can land a hair under
     * the level's threshold at large magnitudes, so a level-proportional
     * margin clears it and a verify loop steps until the level reads
     * back exactly (the overshoot stays far under the display's two
     * decimals for any sane level). Saturates at {@link Double#MAX_VALUE}
     * instead of overflowing; degenerate inputs answer 0.
     *
     * @param level   the level to price (0 or below yields 0)
     * @param base    XP for the level 0 -> 1 step (> 0)
     * @param growth  per-step growth factor (> 0)
     * @return an XP pool whose level read is exactly {@code level}
     */
    public static double xpForLevel(int level, double base, double growth) {
        if (level <= 0 || !(base > 0.0) || !(growth > 0.0)) {
            return 0.0;
        }
        if (growth == 1.0) {
            return base * level;
        }
        double xp = base * Math.expm1(level * Math.log(growth)) / (growth - 1.0);
        if (!Double.isFinite(xp) || xp <= 0.0) {
            return Double.MAX_VALUE;
        }
        xp *= 1.0 + level * 1.0e-14;
        int guard = 0;
        while (level(xp, base, growth) < level && guard++ < 32) {
            xp *= 1.0 + Math.max(1.0e-12, level * 1.0e-14);
        }
        return xp;
    }
}
