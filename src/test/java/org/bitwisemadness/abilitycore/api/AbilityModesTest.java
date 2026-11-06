package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Declared ability modes (issue #113): the ConsumptionStrategies-pattern
 * registry rules - loud ownership, validated mode entries, order preserved
 * for the cycle - plus the client-read side (both sides declare at setup).
 */
class AbilityModesTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final AbilityModes.Mode SINGLE =
            new AbilityModes.Mode("single", "tensura.mode.single");
    private static final AbilityModes.Mode AREA =
            new AbilityModes.Mode("area", "tensura.mode.area");

    @AfterEach
    void isolate() {
        AbilityModes.remove(ABILITY);
    }

    @Test
    void declarationIsLoud() {
        assertThrows(IllegalArgumentException.class, () -> AbilityModes.declare(null, List.of(SINGLE)));
        assertThrows(IllegalArgumentException.class, () -> AbilityModes.declare(ABILITY, null));
        assertThrows(IllegalArgumentException.class, () -> AbilityModes.declare(ABILITY, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AbilityModes.Mode(" ", "k"));
        assertThrows(IllegalArgumentException.class, () -> new AbilityModes.Mode("single", " "));
        assertThrows(IllegalArgumentException.class, () -> new AbilityModes.Mode(null, "k"));

        AbilityModes.declare(ABILITY, List.of(SINGLE, AREA));
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> AbilityModes.declare(ABILITY, List.of(SINGLE)));
        assertTrue(duplicate.getMessage().contains(ABILITY.toString()), duplicate.getMessage());
    }

    @Test
    void duplicateModeIdsInsideOneDeclarationAreRejected() {
        // a repeated id makes the cycle ambiguous (the #111 lesson, kept)
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> AbilityModes.declare(ABILITY, List.of(SINGLE, SINGLE)));
        assertTrue(failure.getMessage().contains("single"), failure.getMessage());
        assertTrue(failure.getMessage().contains(ABILITY.toString()), failure.getMessage());
    }

    @Test
    void declarationsExposeTheOrderedCycle() {
        assertFalse(AbilityModes.has(ABILITY), "nothing declared yet");
        assertTrue(AbilityModes.get(ABILITY).isEmpty());

        AbilityModes.declare(ABILITY, List.of(SINGLE, AREA));
        assertTrue(AbilityModes.has(ABILITY));
        assertEquals(List.of(SINGLE, AREA), AbilityModes.get(ABILITY).orElseThrow(),
                "declaration order is the cycle order");
        assertEquals("tensura.mode.area", AbilityModes.get(ABILITY).orElseThrow().get(1).nameKey());

        assertTrue(AbilityModes.remove(ABILITY));
        assertFalse(AbilityModes.has(ABILITY), "removal restores the undeclared state");
        assertFalse(AbilityModes.remove(ABILITY));
    }

    @Test
    void declarationsAreImmutableCopies() {
        List<AbilityModes.Mode> source = new ArrayList<>(List.of(SINGLE));
        AbilityModes.declare(ABILITY, source);
        source.add(AREA); // caller mutation after declare must not reach the registry
        assertEquals(List.of(SINGLE), AbilityModes.get(ABILITY).orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> AbilityModes.get(ABILITY).orElseThrow().add(AREA));
    }
}
