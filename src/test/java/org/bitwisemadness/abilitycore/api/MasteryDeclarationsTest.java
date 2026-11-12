package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mastery declaration registry and spec validation (REQ-CORE-12): the
 * opt-in contract's guards (loud ownership, share arithmetic, blank
 * names) plus gain resolution - base, custom, and the loud fallbacks a
 * use-time resolution must take instead of failing an activation.
 */
class MasteryDeclarationsTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "mastery_probe");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("tensura", "mastery_other");
    private static final MasteryTarget CATEGORY =
            MasteryTarget.category(ResourceLocation.fromNamespaceAndPath("tensura", "fire"));

    @AfterEach
    void isolate() {
        MasteryDeclarations.remove(ABILITY);
        MasteryDeclarations.remove(OTHER);
        MasteryGainHandlers.remove("test:gain");
    }

    @Test
    void gainedBuildsTheCommonCase() {
        MasterySpec spec = MasterySpec.gained(10.0);
        assertEquals(10.0, spec.baseGain());
        assertEquals(Set.of(MasteryTrigger.ACTIVATION), spec.triggers());
        assertTrue(spec.shares().isEmpty());
        assertEquals(null, spec.gainHandler());
        assertTrue(spec.effects().isEmpty());
    }

    @Test
    void specValidationRejectsBadShapes() {
        assertThrows(IllegalArgumentException.class, () -> MasterySpec.gained(-1.0));
        assertThrows(IllegalArgumentException.class, () -> MasterySpec.gained(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> MasterySpec.gained(1.0).withShare(CATEGORY, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> MasterySpec.gained(1.0).withShare(CATEGORY, 1.5));
        assertThrows(IllegalArgumentException.class,
                () -> MasterySpec.gained(1.0).withShare(null, 0.5));
        // shares may not exceed 1 - self keeps the (non-negative) remainder
        assertThrows(IllegalArgumentException.class, () -> MasterySpec.gained(1.0)
                .withShare(CATEGORY, 0.6)
                .withShare(MasteryTarget.ability(OTHER), 0.6));
        assertThrows(IllegalArgumentException.class,
                () -> MasterySpec.gained(1.0).withGainHandler("  "));
        assertThrows(IllegalArgumentException.class,
                () -> MasterySpec.gained(1.0).withEffect(null));
        assertThrows(IllegalArgumentException.class,
                () -> MasterySpec.gained(1.0).withEffect(MasteryAxis.COST, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new MasterySpec(1.0, null, List.of(), null, Map.of()));
        // fluent calls never mutate the original
        MasterySpec base = MasterySpec.gained(1.0);
        base.withShare(CATEGORY, 0.5);
        assertTrue(base.shares().isEmpty(), "withShare returns a copy");
    }

    @Test
    void declareEnforcesLoudOwnership() {
        assertThrows(IllegalArgumentException.class, () -> MasteryDeclarations.declare(null,
                MasterySpec.gained(1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> MasteryDeclarations.declare(ABILITY, null));
        MasteryDeclarations.declare(ABILITY, MasterySpec.gained(5.0));
        assertThrows(IllegalStateException.class, () -> MasteryDeclarations.declare(ABILITY,
                MasterySpec.gained(7.0)), "two owners claim one ability - never overwrite");
        assertTrue(MasteryDeclarations.has(ABILITY));
        assertEquals(5.0, MasteryDeclarations.get(ABILITY).orElseThrow().baseGain());
        assertFalse(MasteryDeclarations.has(OTHER));
        assertFalse(MasteryDeclarations.has(null));
        assertTrue(MasteryDeclarations.remove(ABILITY));
        assertFalse(MasteryDeclarations.remove(ABILITY));
    }

    @Test
    void undeclaredAbilitiesAreInvisibleToTheSystem() {
        assertFalse(MasteryDeclarations.has(OTHER));
        assertTrue(MasteryDeclarations.get(OTHER).isEmpty());
        assertFalse(MasteryDeclarations.remove(OTHER), "absent removal is a false, not a throw");
    }

    @Test
    void resolveGainUsesTheBaseWithoutAHandler() {
        MasterySpec spec = MasterySpec.gained(12.0);
        assertEquals(12.0, MasteryAwards.resolveGain(spec, null, ABILITY,
                MasteryTrigger.ACTIVATION));
    }

    @Test
    void resolveGainPrefersTheNamedHandler() {
        MasteryGainHandlers.declare("test:gain", (player, source, trigger) -> 42.0);
        MasterySpec spec = MasterySpec.gained(12.0).withGainHandler("test:gain");
        assertEquals(42.0, MasteryAwards.resolveGain(spec, null, ABILITY,
                MasteryTrigger.HIT), "the handler sees the trigger and replaces the base");
    }

    @Test
    void resolveGainFallsBackLoudlyInsteadOfThrowing() {
        // unknown name: use-time resolution, base gain, never an exception
        MasterySpec unknown = MasterySpec.gained(12.0).withGainHandler("test:vanished");
        assertEquals(12.0, MasteryAwards.resolveGain(unknown, null, ABILITY,
                MasteryTrigger.ACTIVATION));

        // a handler returning garbage falls back to the base gain
        MasteryGainHandlers.declare("test:gain", (player, source, trigger) -> -5.0);
        MasterySpec negative = MasterySpec.gained(12.0).withGainHandler("test:gain");
        assertEquals(12.0, MasteryAwards.resolveGain(negative, null, ABILITY,
                MasteryTrigger.ACTIVATION));

        // a throwing handler falls back to the base gain
        MasteryGainHandlers.remove("test:gain");
        MasteryGainHandlers.declare("test:gain", (player, source, trigger) -> {
            throw new IllegalStateException("boom");
        });
        assertEquals(12.0, MasteryAwards.resolveGain(negative, null, ABILITY,
                MasteryTrigger.ACTIVATION));

        // a zero gain is a valid answer (the award path skips it downstream)
        MasteryGainHandlers.remove("test:gain");
        MasteryGainHandlers.declare("test:gain", (player, source, trigger) -> 0.0);
        assertEquals(0.0, MasteryAwards.resolveGain(negative, null, ABILITY,
                MasteryTrigger.ACTIVATION));
    }

    @Test
    void maxLevelDefaultsToAHundredAndFluentCopiesCarryIt() {
        assertEquals(MasterySpec.DEFAULT_MAX_LEVEL, MasterySpec.gained(1.0).maxLevel());
        assertEquals(100, MasterySpec.gained(1.0).maxLevel(),
                "the shipped default cap is level 100");
        assertEquals(50, MasterySpec.gained(1.0).withMaxLevel(50).maxLevel());
        assertEquals(0, MasterySpec.gained(1.0).withMaxLevel(0).maxLevel(),
                "0 = uncapped");
        assertEquals(50, MasterySpec.gained(1.0).withMaxLevel(50)
                        .withGainHandler("test:carry").maxLevel(),
                "every fluent copy carries the cap");
        // the pre-cap constructor shape still compiles and applies the default
        assertEquals(MasterySpec.DEFAULT_MAX_LEVEL,
                new MasterySpec(1.0, Set.of(MasteryTrigger.ACTIVATION), List.of(),
                        null, Map.of()).maxLevel());
        assertThrows(IllegalArgumentException.class,
                () -> MasterySpec.gained(1.0).withMaxLevel(-1),
                "a negative cap is rejected loudly");
    }
}
