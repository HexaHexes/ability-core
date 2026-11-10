package org.bitwisemadness.abilitycore.mastery;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.slf4j.Logger;

/**
 * The XP-to-level source of truth (REQ-CORE-12): a datapack threshold
 * table when one is loaded, otherwise the server-config formula. The
 * table is <b>authoritative while present</b> - it defines both the
 * steps and the cap (its length) - so a pack that ships
 * {@code data/<ns>/mastery/levels.json} owns progression pacing outright.
 *
 * <p>Exactly-one rule (the fleet's loud-ownership convention): zero
 * tables = formula, one table = it wins, several = an error naming every
 * owner and the formula (a conflict is an authoring bug, never a silent
 * pick). All state is a single volatile reference - a reload swaps it
 * atomically, readers never see a half-installed table.</p>
 */
public final class MasteryLevels {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ascending cumulative XP thresholds; null = the formula path. */
    private static volatile double[] thresholds;

    private MasteryLevels() {
    }

    /** Whether a datapack table currently overrides the formula. */
    public static boolean hasTable() {
        return thresholds != null;
    }

    /**
     * The level an XP pool reaches: the table when present (count of
     * thresholds at or below the pool, no cap beyond the table's length),
     * otherwise the config formula.
     *
     * @param xp raw XP (degenerate inputs read as level 0)
     * @return the level
     */
    public static int level(double xp) {
        double[] table = thresholds;
        if (table != null) {
            if (!Double.isFinite(xp) || xp <= 0.0) {
                return 0;
            }
            int level = 0;
            while (level < table.length && xp >= table[level]) {
                level++;
            }
            return level;
        }
        return MasteryCurve.level(xp, CoreConfig.masteryMaxLevel(),
                CoreConfig.masteryLevelCurveBase(), CoreConfig.masteryLevelCurveGrowth());
    }

    /**
     * Parses one datapack table ({@code {"levels": [100, 250, ...]}}):
     * non-empty, finite, non-negative and strictly ascending, else an
     * {@link IllegalArgumentException} naming the problem (the listener
     * logs it and treats the file as absent - fail to the formula).
     *
     * @param root the file's JSON
     * @return the cumulative thresholds
     */
    static double[] parseLevels(JsonElement root) {
        if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("levels")
                || !root.getAsJsonObject().get("levels").isJsonArray()) {
            throw new IllegalArgumentException("expected an object with a 'levels' array");
        }
        var array = root.getAsJsonObject().get("levels").getAsJsonArray();
        if (array.size() == 0) {
            throw new IllegalArgumentException("'levels' must not be empty (use the formula instead)");
        }
        double[] table = new double[array.size()];
        double previous = -1.0;
        for (int i = 0; i < array.size(); i++) {
            double value;
            try {
                value = array.get(i).getAsDouble();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "level " + i + " is not a number: " + array.get(i));
            }
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "level " + i + " must be finite and >= 0, got " + value);
            }
            if (value <= previous) {
                throw new IllegalArgumentException(
                        "levels must strictly ascend: " + value + " after " + previous);
            }
            table[i] = value;
            previous = value;
        }
        return table;
    }

    /**
     * The exactly-one install rule: zero tables clears to the formula,
     * one table installs it, several log every owner and clear to the
     * formula (never a silent pick between packs).
     *
     * @param tables validated tables by resource id (may be empty)
     */
    static void applyTables(Map<ResourceLocation, double[]> tables) {
        if (tables.isEmpty()) {
            thresholds = null;
            return;
        }
        if (tables.size() > 1) {
            LOGGER.error("Multiple mastery level tables loaded ({}): {}. "
                            + "Exactly one pack may ship mastery/levels.json - using the formula",
                    tables.size(), tables.keySet());
            thresholds = null;
            return;
        }
        thresholds = tables.values().iterator().next();
    }

    /** Test isolation: installs or clears the table directly. */
    static void installForTests(double[] table) {
        thresholds = table;
    }
}
