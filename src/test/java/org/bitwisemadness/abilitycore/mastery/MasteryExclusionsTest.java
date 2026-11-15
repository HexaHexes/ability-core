package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasteryExclusions;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The domain-exclusion seam (owner directive 2026-11-15): a provider's
 * declared ids join the blacklist read at the one union point, so every
 * award/effect/query gate that already asks {@code masteryBlacklisted}
 * answers for them without a single call-site change - and the config
 * list keeps its additive operator role.
 */
class MasteryExclusionsTest {

    private static final ResourceLocation RESISTANCE =
            ResourceLocation.fromNamespaceAndPath("tensura", "cold_resistance");
    private static final ResourceLocation NULLIFICATION =
            ResourceLocation.fromNamespaceAndPath("tensura", "magic_nullification");
    private static final ResourceLocation ORDINARY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fire_ball");

    @AfterEach
    void isolate() {
        MasteryExclusions.remove(RESISTANCE);
        MasteryExclusions.remove(NULLIFICATION);
        MasteryExclusions.remove(ORDINARY);
    }

    @Test
    void declaredIdsJoinTheBlacklistRead() {
        // the default state: nothing declared, the union answers false
        assertFalse(CoreConfig.masteryBlacklisted(RESISTANCE),
                "an undeclared id must not be excluded by default");
        MasteryExclusions.declare(RESISTANCE);
        assertTrue(MasteryExclusions.excluded(RESISTANCE));
        assertTrue(CoreConfig.masteryBlacklisted(RESISTANCE),
                "the blacklist read must be the union of config and domain defaults");
        // an ordinary art stays untouched
        assertFalse(CoreConfig.masteryBlacklisted(ORDINARY));
    }

    @Test
    void declarationsAreIdempotentAndRemovable() {
        MasteryExclusions.declare(RESISTANCE);
        MasteryExclusions.declare(RESISTANCE);
        assertTrue(CoreConfig.masteryBlacklisted(RESISTANCE));
        MasteryExclusions.remove(RESISTANCE);
        assertFalse(CoreConfig.masteryBlacklisted(RESISTANCE),
                "remove restores the plain config answer (test isolation)");
    }

    @Test
    void declareAllCoversABatchAndNullNeverMatches() {
        MasteryExclusions.declareAll(java.util.List.of(RESISTANCE, NULLIFICATION));
        assertTrue(CoreConfig.masteryBlacklisted(RESISTANCE));
        assertTrue(CoreConfig.masteryBlacklisted(NULLIFICATION));
        // the fail-closed edges: null ids never throw, never match
        MasteryExclusions.declare(null);
        MasteryExclusions.remove(null);
        assertFalse(MasteryExclusions.excluded(null));
        assertFalse(CoreConfig.masteryBlacklisted(null));
    }
}
