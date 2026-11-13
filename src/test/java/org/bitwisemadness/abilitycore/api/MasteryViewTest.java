package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.mastery.MasteryCurve;
import org.junit.jupiter.api.Test;

/**
 * The mastery view aggregation (REQ-CORE-12): levels derive from the
 * curve, and the aggregate that effect handlers scale on is
 * ownLevel + share-weighted target levels - the same list that routes XP
 * out empowers the caster back.
 */
class MasteryViewTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "view_probe");
    private static final MasteryTarget FIRE =
            MasteryTarget.category(ResourceLocation.fromNamespaceAndPath("tensura", "fire"));
    private static final MasteryTarget BLAZE =
            MasteryTarget.ability(ResourceLocation.fromNamespaceAndPath("tensura", "blaze"));

    // linear curve: base 100, growth 1 -> every 100 XP is one level
    private static MasteryView view(MasterySpec spec, double ownXp,
            Map<MasteryTarget, Double> stored) {
        return MasteryView.build(spec, ownXp, target -> stored.getOrDefault(target, 0.0),
                xp -> MasteryCurve.level(xp, 100.0, 1.0), Integer.MAX_VALUE);
    }

    @Test
    void aggregateIsOwnLevelPlusShareWeightedTargetLevels() {
        MasterySpec spec = MasterySpec.gained(10.0)
                .withShare(FIRE, 0.5)
                .withShare(BLAZE, 0.25);
        MasteryView built = view(spec, 300.0, Map.of(FIRE, 100.0, BLAZE, 200.0));

        assertEquals(3, built.ownLevel(), "300 XP on a 100-step curve");
        assertEquals(1, built.targets().get(0).level(), "fire at 100 XP = level 1");
        assertEquals(2, built.targets().get(1).level(), "blaze at 200 XP = level 2");
        // 3 + 0.5*1 + 0.25*2 = 4.0
        assertEquals(4.0, built.aggregateLevel(), 1e-9);
        assertEquals(300.0, built.ownXp());
    }

    @Test
    void noSharesMeansTheAggregateIsTheOwnLevel() {
        MasteryView built = view(MasterySpec.gained(10.0), 250.0, Map.of());
        assertEquals(2, built.ownLevel());
        assertEquals(2.0, built.aggregateLevel(), 1e-9);
        assertTrue(built.targets().isEmpty());
    }

    @Test
    void emptyViewIsInert() {
        MasteryView empty = MasteryView.empty();
        assertEquals(0.0, empty.ownXp());
        assertEquals(0, empty.ownLevel());
        assertEquals(0.0, empty.aggregateLevel(), 1e-9);
        assertTrue(empty.targets().isEmpty());
    }

    @Test
    void targetsAreUnmodifiable() {
        MasteryView built = view(MasterySpec.gained(1.0).withShare(FIRE, 1.0), 0.0, Map.of());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> built.targets().add(null), "view records never leak mutable lists");
    }

    @Test
    void hardCeilingClampsTheOwnLevelAndAggregate() {
        MasterySpec spec = MasterySpec.gained(10.0);
        var capped = MasteryView.build(spec, 10_000.0, target -> 0.0,
                xp -> MasteryCurve.level(xp, 100.0, 1.15), 3);
        assertEquals(3, capped.ownLevel(), "a HARD ceiling stops the own level");
        assertEquals(3.0, capped.aggregateLevel(), 1e-9,
                "and the aggregate carries the clamped term (no shares)");
        var uncapped = MasteryView.build(spec, 10_000.0, target -> 0.0,
                xp -> MasteryCurve.level(xp, 100.0, 1.15), Integer.MAX_VALUE);
        assertTrue(uncapped.ownLevel() > 3, "no ceiling, no clamp");

        // a share feeds the aggregate too (the school-mastery shape): a
        // HARD ceiling clamps the EFFECT INPUT - the caps brief's
        // "view/effect input clamps" - while SOFT keeps the full sum
        MasterySpec shared = spec.withShare(FIRE, 0.5);
        var hardShared = MasteryView.build(shared, 10_000.0, target -> 1_000.0,
                xp -> MasteryCurve.level(xp, 100.0, 1.15), 3);
        assertEquals(3.0, hardShared.aggregateLevel(), 1e-9,
                "a share can never push the aggregate past the ceiling, got "
                        + hardShared.aggregateLevel());
        var softShared = MasteryView.build(shared, 10_000.0, target -> 1_000.0,
                xp -> MasteryCurve.level(xp, 100.0, 1.15), Integer.MAX_VALUE);
        assertTrue(softShared.aggregateLevel() > 3.0,
                "SOFT keeps the full aggregate, got " + softShared.aggregateLevel());
    }

    @Test
    void ownLevelOnlyDropsTheShareTermsButKeepsTheTargets() {
        // channel v6's "no mastery ability feeds this" read: the pane's
        // parenthetical subtracts it from the full read, so the share
        // terms must go while the target list stays for handlers
        MasterySpec spec = MasterySpec.gained(10.0)
                .withShare(FIRE, 0.5)
                .withShare(BLAZE, 0.25);
        MasteryView built = view(spec, 300.0, Map.of(FIRE, 100.0, BLAZE, 200.0));
        MasteryView own = built.ownLevelOnly();

        assertEquals(3.0, own.aggregateLevel(), 1e-9,
                "the aggregate collapses to the own level alone");
        assertEquals(3, own.ownLevel(), "the own level is untouched");
        assertEquals(300.0, own.ownXp(), "the own XP is untouched");
        assertEquals(built.targets(), own.targets(),
                "the share list stays - a view-reading handler answers alike");
    }
}
