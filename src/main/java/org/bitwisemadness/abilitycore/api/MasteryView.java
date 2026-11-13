package org.bitwisemadness.abilitycore.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilitycore.mastery.MasteryLevels;
import org.bitwisemadness.abilitycore.mastery.MasteryOverlays;
import org.jetbrains.annotations.Nullable;

/**
 * One caster's mastery position for one ability (REQ-CORE-12): the
 * ability's own XP and level, plus the aggregate level
 * effect handlers scale on -
 * {@code ownLevel + Σ(targetLevel)} over the spec's share list:
 * every share target contributes its FULL level, so routing half a
 * grant to a fire category still empowers the caster by that
 * category's whole level. Shares route XP only - they never weight
 * the effect input (the full-contributions rule: an ability drawing
 * on a target receives that target's whole mastery bonus).
 *
 * @param ownXp         raw XP stored on the ability's own target
 * @param ownLevel      the level {@code ownXp} reaches on the curve
 * @param aggregateLevel the level effect handlers consume
 * @param targets       per-target levels with their shares (unmodifiable)
 */
public record MasteryView(
        double ownXp,
        int ownLevel,
        double aggregateLevel,
        List<TargetLevel> targets) {

    /**
     * One contributed target's position.
     *
     * @param target the contribution target
     * @param level  the level its stored XP reaches on the curve
     * @param share  the spec's share for this target (its XP-routing
     *               share - never weights the aggregate)
     */
    public record TargetLevel(MasteryTarget target, int level, double share) {
    }

    public MasteryView {
        if (targets == null) {
            throw new IllegalArgumentException("targets must not be null");
        }
        targets = List.copyOf(targets);
    }

    /** The empty view: undeclared ability, or no player to read. */
    public static MasteryView empty() {
        return new MasteryView(0.0, 0, 0.0, List.of());
    }

    /**
     * The same view with the aggregate reset to the ability's OWN level -
     * the "no mastery ability feeds this" read the pane's parenthetical
     * (channel v6) subtracts. Targets stay: a named handler that reads
     * them directly answers the same value twice, so its slice reads 0 -
     * the handler's design, honestly reported.
     */
    public MasteryView ownLevelOnly() {
        return new MasteryView(ownXp, ownLevel, ownLevel, targets);
    }

    /**
     * The view for one player and ability, read live from the player's
     * stored mastery and the spec's share list.
     *
     * @param player   the caster (null yields {@link #empty()})
     * @param abilityId the declared ability
     * @return the current view
     */
    public static MasteryView of(@Nullable ServerPlayer player, ResourceLocation abilityId) {
        MasterySpec spec = MasteryOverlays.specFor(abilityId);
        if (spec == null || player == null) {
            return empty();
        }
        var data = MasteryAttachments.data(player);
        return build(spec, data.xp(MasteryTarget.ability(abilityId).key()),
                target -> data.xp(target.key()), MasteryLevels::level,
                hardCapCeiling(abilityId, spec));
    }

    /**
     * The ceiling a HARD cap puts on this ability's own level: the spec's
     * {@code maxLevel} under a HARD mode, otherwise unbounded - SOFT
     * modes let the level grow (their cap is the reduced XP, not the
     * number) and an uncapped spec (0) never clamps.
     */
    private static int hardCapCeiling(ResourceLocation abilityId, MasterySpec spec) {
        return spec.maxLevel() > 0
                && CoreConfig.masteryCapMode(abilityId) == CoreConfig.CapMode.HARD
                ? spec.maxLevel()
                : Integer.MAX_VALUE;
    }

    /**
     * The pure builder (fixtures read this directly): levels come from the
     * supplied function (the datapack table or the config formula in
     * production), the aggregate from the share list at FULL strength -
     * own level plus every share target's level, self excluded (its
     * own level is the base term); shares route XP (MasteryRouting) and
     * never weight this input. A HARD cap's {@code ceiling} clamps
     * BOTH the own level and the aggregate - the view feeds the effect
     * curves, so a full contribution can never push the effect input
     * past the cap (SOFT ceilings are {@link Integer#MAX_VALUE} and
     * never bite).
     *
     * @param spec    the declared contract
     * @param ownXp   XP on the ability's own target
     * @param xpOf    XP lookup for an arbitrary target
     * @param levelFn the level function the curve resolves through
     * @param ceiling the HARD-cap ceiling (or {@link Integer#MAX_VALUE})
     * @return the assembled view
     */
    static MasteryView build(MasterySpec spec, double ownXp,
            Function<MasteryTarget, Double> xpOf, ToIntFunction<Double> levelFn,
            int ceiling) {
        int ownLevel = Math.min(levelFn.applyAsInt(ownXp), ceiling);
        double aggregate = ownLevel;
        List<TargetLevel> targets = new ArrayList<>(spec.shares().size());
        for (MasterySpec.Share share : spec.shares()) {
            int level = levelFn.applyAsInt(xpOf.apply(share.target()));
            // Full contributions: the share routes XP (MasteryRouting)
            // but never weights this input - every target counts at
            // its own level: a spell gets its element's whole bonus.
            aggregate += level;
            targets.add(new TargetLevel(share.target(), level, share.share()));
        }
        // The caps brief: the EFFECT INPUT clamps too - a full
        // contribution can never carry a HARD stop past the ceiling
        // (SOFT never bites); target levels stay raw under their caps
        aggregate = Math.min(aggregate, ceiling);
        return new MasteryView(ownXp, ownLevel, aggregate, targets);
    }
}
