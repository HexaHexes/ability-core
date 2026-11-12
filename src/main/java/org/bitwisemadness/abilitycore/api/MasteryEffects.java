package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.bitwisemadness.abilitycore.mastery.MasteryOverlays;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The mastery effect read API (REQ-CORE-12): four multipliers, one per
 * {@link MasteryAxis}. Every read is inert ({@code 1.0}) unless the
 * system is enabled, the ability declared a spec, and the axis
 * participates - so an undeclared fleet pays nothing for this system.
 *
 * <p>Core applies {@link MasteryAxis#COST} itself inside the resource
 * charge step; the other axes (and NATIVE-charged costs such as ISS mana)
 * are read here and applied by the domain that owns the semantics. A
 * named custom handler replaces the default curve for its axis; a
 * handler that is unknown, throws, or returns a non-positive/non-finite
 * multiplier logs an error and the default curve applies instead - an
 * effect read never fails an activation.</p>
 */
public final class MasteryEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MasteryEffects() {
    }

    /** The cost multiplier for one cast ({@code (0, 1]}, 1 = no discount). */
    public static double costMultiplier(@Nullable ServerPlayer player, ResourceLocation abilityId) {
        return multiplier(player, abilityId, MasteryAxis.COST);
    }

    /** The cast-time multiplier for one cast ({@code (0, 1]}, 1 = no reduction). */
    public static double castTimeMultiplier(@Nullable ServerPlayer player,
            ResourceLocation abilityId) {
        return multiplier(player, abilityId, MasteryAxis.CAST_TIME);
    }

    /** The power multiplier for one cast ({@code >= 1}, 1 = no bonus). */
    public static double powerMultiplier(@Nullable ServerPlayer player, ResourceLocation abilityId) {
        return multiplier(player, abilityId, MasteryAxis.POWER);
    }

    /** The duration multiplier for one cast ({@code >= 1}, 1 = no bonus). */
    public static double durationMultiplier(@Nullable ServerPlayer player,
            ResourceLocation abilityId) {
        return multiplier(player, abilityId, MasteryAxis.DURATION);
    }

    /**
     * The multiplier for one axis, read against the given view - the
     * effect read behind the four axis conveniences and the query
     * answer's bonus list (REQ-CORE-12). Inert ({@code 1.0}) unless the
     * system is enabled, the ability declared a spec, and the axis
     * participates; a named handler replaces the default curve and any
     * handler failure logs and falls back - an effect read never fails.
     *
     * @param player   the caster (null reads as disabled)
     * @param abilityId the declared ability
     * @param axis     the effect axis
     * @return the multiplier in the axis's honest range
     */
    public static double multiplier(@Nullable ServerPlayer player, ResourceLocation abilityId,
            MasteryAxis axis) {
        if (!CoreConfig.masteryEnabled() || player == null || abilityId == null) {
            return 1.0;
        }
        MasterySpec spec = MasteryOverlays.specFor(abilityId);
        if (spec == null) {
            return 1.0; // opted out
        }
        MasterySpec.EffectRule rule = spec.effects().get(axis);
        if (rule == null) {
            return 1.0; // this axis does not participate
        }
        MasteryView view = MasteryView.of(player, abilityId);
        if (rule.handler() != null) {
            MasteryEffectHandlers.EffectHandler handler =
                    MasteryEffectHandlers.get(axis, rule.handler()).orElse(null);
            if (handler == null) {
                LOGGER.error("Unknown mastery effect handler '{}' for {} ({}) - default curve",
                        rule.handler(), abilityId, axis);
            } else {
                try {
                    double value = handler.multiplier(player, abilityId, view);
                    if (Double.isFinite(value) && value > 0.0) {
                        return value;
                    }
                    LOGGER.error("Mastery effect handler '{}' for {} ({}) returned {} - default curve",
                            rule.handler(), abilityId, axis, value);
                } catch (RuntimeException e) {
                    LOGGER.error("Mastery effect handler '{}' for {} ({}) threw - default curve",
                            rule.handler(), abilityId, axis, e);
                }
            }
        }
        return defaultMultiplier(axis, view.aggregateLevel());
    }

    /**
     * The default curve for one axis at an aggregate level: an
     * asymptotic approach to the configured bound - reductions are
     * {@code 1 - approach(max, rate, level)} and bonuses
     * {@code 1 + approach(max, rate, level)}, where the approach is
     * {@code max * (1 - e^(-rate * level))}: it nears X/Y/Z/A (the max*
     * config keys) as the level grows without bound and never crosses
     * them. Package-private so fixtures pin the math without a player.
     */
    static double defaultMultiplier(MasteryAxis axis, double aggregateLevel) {
        double level = Math.max(0.0, aggregateLevel);
        return switch (axis) {
            case COST -> 1.0 - approach(CoreConfig.masteryMaxCostReduction(),
                    CoreConfig.masteryCostReductionPerLevel(), level);
            case CAST_TIME -> 1.0 - approach(CoreConfig.masteryMaxCastTimeReduction(),
                    CoreConfig.masteryCastTimeReductionPerLevel(), level);
            case POWER -> 1.0 + approach(CoreConfig.masteryMaxPowerIncrease(),
                    CoreConfig.masteryPowerIncreasePerLevel(), level);
            case DURATION -> 1.0 + approach(CoreConfig.masteryMaxDurationIncrease(),
                    CoreConfig.masteryDurationIncreasePerLevel(), level);
        };
    }

    /**
     * The asymptote: {@code max * (1 - e^(-rate * level))} - 0 at level 0,
     * strictly increasing, never reaching {@code max} at any finite level.
     */
    static double approach(double max, double rate, double level) {
        if (!(max > 0.0) || !(rate > 0.0) || !(level > 0.0)) {
            return 0.0;
        }
        return max * (1.0 - Math.exp(-rate * level));
    }
}
