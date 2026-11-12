package org.bitwisemadness.abilitycore.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * One ability's opt-in mastery contract (REQ-CORE-12): how much a use
 * grants, which triggers grant, where the experience flows (share list,
 * self keeps the remainder), and which effect axes participate - each with
 * core's default curve or a named custom handler.
 *
 * <p>Undeclared abilities never touch the system: no storage writes, no
 * effect reads. Build specs fluently at mod construction
 * ({@link MasteryDeclarations#declare}); every {@code with...} method
 * returns a validated copy.</p>
 *
 * <pre>
 * MasteryDeclarations.declare(FIREBALL, MasterySpec.gained(10.0)
 *         .withShare(MasteryTarget.category(FIRE_CATEGORY), 0.5)
 *         .withEffect(MasteryAxis.COST));
 * </pre>
 *
 * @param baseGain      XP per trigger when no gain handler is named
 * @param triggers      the triggers that grant (default {@code ACTIVATION})
 * @param shares        external targets and their share of each grant
 *                      (sum {@code <= 1}; self keeps the remainder)
 * @param gainHandler   named {@link MasteryGainHandlers} entry replacing
 *                      {@code baseGain} when present
 * @param effects       participating axes to their rule; an absent axis
 *                      does not participate at all
 * @param maxLevel      the ability's mastery cap ({@link #DEFAULT_MAX_LEVEL}
 *                      by default; {@code 0} = uncapped) - HARD modes stop
 *                      the level here, SOFT modes keep growing past it
 *                      with reduced XP (the per-integration config switch)
 */
public record MasterySpec(
        double baseGain,
        Set<MasteryTrigger> triggers,
        List<Share> shares,
        @Nullable String gainHandler,
        Map<MasteryAxis, EffectRule> effects,
        int maxLevel) {

    /** The default cap when an ability does not declare one (owner: 100). */
    public static final int DEFAULT_MAX_LEVEL = 100;

    /**
     * One external contribution target and its share of every grant
     * (0 exclusive, sum with all siblings {@code <= 1}).
     *
     * @param target where this share flows
     * @param share  fraction of each grant, (0, 1]
     */
    public record Share(MasteryTarget target, double share) {

        public Share {
            if (target == null) {
                throw new IllegalArgumentException("share target must not be null");
            }
            if (!Double.isFinite(share) || share <= 0.0 || share > 1.0) {
                throw new IllegalArgumentException(
                        "share must be in (0, 1], got " + share + " for " + target);
            }
        }
    }

    /**
     * One participating effect axis: an optional named custom handler
     * ({@code null} = core's default curve for the axis, scaled by the
     * caster's aggregate mastery level).
     *
     * @param handler the {@link MasteryEffectHandlers} name, or null for
     *                the default curve
     */
    public record EffectRule(@Nullable String handler) {

        public EffectRule {
            if (handler != null && handler.isBlank()) {
                throw new IllegalArgumentException("effect handler name must not be blank");
            }
        }

        /** The default-curve rule for an axis. */
        public static EffectRule defaultCurve() {
            return new EffectRule(null);
        }
    }

    public MasterySpec {
        if (!Double.isFinite(baseGain) || baseGain < 0.0) {
            throw new IllegalArgumentException("baseGain must be finite and >= 0, got " + baseGain);
        }
        if (maxLevel < 0) {
            throw new IllegalArgumentException(
                    "maxLevel must be >= 0 (0 = uncapped), got " + maxLevel);
        }
        if (triggers == null) {
            throw new IllegalArgumentException("triggers must not be null");
        }
        if (shares == null) {
            throw new IllegalArgumentException("shares must not be null");
        }
        double sum = 0.0;
        for (Share share : shares) {
            if (share == null) {
                throw new IllegalArgumentException("shares must not contain null");
            }
            sum += share.share();
        }
        if (sum > 1.0 + 1.0e-9) {
            throw new IllegalArgumentException(
                    "share sum must be <= 1 (self keeps the remainder), got " + sum);
        }
        if (gainHandler != null && gainHandler.isBlank()) {
            throw new IllegalArgumentException("gainHandler must not be blank");
        }
        if (effects == null) {
            throw new IllegalArgumentException("effects must not be null");
        }
        for (Map.Entry<MasteryAxis, EffectRule> entry : effects.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("effects must map a non-null axis to a rule");
            }
        }
    }

    /**
     * Source-compatible constructor for the pre-cap shape: the default
     * cap ({@link #DEFAULT_MAX_LEVEL}) applies.
     */
    public MasterySpec(double baseGain, Set<MasteryTrigger> triggers, List<Share> shares,
            @Nullable String gainHandler, Map<MasteryAxis, EffectRule> effects) {
        this(baseGain, triggers, shares, gainHandler, effects, DEFAULT_MAX_LEVEL);
    }

    /**
     * A new spec granting {@code baseGain} on every successful activation
     * (the common case: one trigger, self-remainder only, no effects) with
     * the default cap.
     *
     * @param baseGain XP per activation, finite and >= 0
     * @return the base spec for fluent {@code with...} calls
     */
    public static MasterySpec gained(double baseGain) {
        return new MasterySpec(baseGain, Set.of(MasteryTrigger.ACTIVATION),
                List.of(), null, Map.of(), DEFAULT_MAX_LEVEL);
    }

    /** A copy adding triggers (idempotent per trigger). */
    public MasterySpec withTriggers(MasteryTrigger... more) {
        Set<MasteryTrigger> merged = new HashSet<>(triggers);
        Collections.addAll(merged, more);
        return new MasterySpec(baseGain, Set.copyOf(merged), shares, gainHandler, effects,
                maxLevel);
    }

    /** A copy routing one more share of every grant to {@code target}. */
    public MasterySpec withShare(MasteryTarget target, double share) {
        List<Share> merged = new ArrayList<>(shares);
        merged.add(new Share(target, share));
        return new MasterySpec(baseGain, triggers, List.copyOf(merged), gainHandler, effects,
                maxLevel);
    }

    /** A copy whose gain comes from the named handler instead of baseGain. */
    public MasterySpec withGainHandler(String handlerName) {
        return new MasterySpec(baseGain, triggers, shares, handlerName, effects, maxLevel);
    }

    /** A copy participating in {@code axis} on core's default curve. */
    public MasterySpec withEffect(MasteryAxis axis) {
        return withRule(axis, EffectRule.defaultCurve());
    }

    /** A copy participating in {@code axis} through a named custom handler. */
    public MasterySpec withEffect(MasteryAxis axis, String handlerName) {
        return withRule(axis, new EffectRule(handlerName));
    }

    /**
     * A copy capped at {@code level} - HARD modes stop the level here,
     * SOFT modes grow past it with reduced XP; {@code 0} = uncapped.
     */
    public MasterySpec withMaxLevel(int level) {
        if (level < 0) {
            throw new IllegalArgumentException(
                    "maxLevel must be >= 0 (0 = uncapped), got " + level);
        }
        return new MasterySpec(baseGain, triggers, shares, gainHandler, effects, level);
    }

    private MasterySpec withRule(MasteryAxis axis, EffectRule rule) {
        if (axis == null) {
            throw new IllegalArgumentException("axis must not be null");
        }
        Map<MasteryAxis, EffectRule> merged = new EnumMap<>(MasteryAxis.class);
        merged.putAll(effects);
        merged.put(axis, rule);
        return new MasterySpec(baseGain, triggers, shares, gainHandler,
                Collections.unmodifiableMap(merged), maxLevel);
    }
}
