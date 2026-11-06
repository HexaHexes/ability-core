package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Declared unlock providers (issue #117): the AvailabilityProviders-pattern
 * registry rules - loud ownership, one provider per ability - plus the
 * option-typed answer discipline: unknown stays unknown (undeclared,
 * abstaining, or null-returning provider), never a guessed boolean
 * (REQ-HOT-08).
 */
class AbilityUnlocksTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("tensura", "waterball");

    @AfterEach
    void isolate() {
        AbilityUnlocks.remove(ABILITY);
        AbilityUnlocks.remove(OTHER);
    }

    @Test
    void declarationIsLoud() {
        assertThrows(IllegalArgumentException.class,
                () -> AbilityUnlocks.declare(null, (player, id) -> Optional.of(true)));
        assertThrows(IllegalArgumentException.class, () -> AbilityUnlocks.declare(ABILITY, null));

        AbilityUnlocks.declare(ABILITY, (player, id) -> Optional.of(true));
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> AbilityUnlocks.declare(ABILITY, (player, id) -> Optional.of(false)));
        assertTrue(duplicate.getMessage().contains(ABILITY.toString()), duplicate.getMessage());
        assertTrue(AbilityUnlocks.has(ABILITY));
    }

    @Test
    void undeclaredAbilityIsUnknown() {
        assertTrue(AbilityUnlocks.isLearned(null, OTHER).isEmpty(),
                "an undeclared ability must answer unknown, never false");
        assertTrue(AbilityUnlocks.isLearned(null, null).isEmpty(),
                "a null id must answer unknown");
    }

    @Test
    void providerAnswersArePassedThroughVerbatim() {
        AbilityUnlocks.declare(ABILITY, (player, id) -> Optional.of(true));
        assertEquals(Optional.of(Boolean.TRUE), AbilityUnlocks.isLearned(null, ABILITY),
                "true must read as learned");

        AbilityUnlocks.remove(ABILITY);
        AbilityUnlocks.declare(ABILITY, (player, id) -> Optional.of(false));
        assertEquals(Optional.of(Boolean.FALSE), AbilityUnlocks.isLearned(null, ABILITY),
                "false must read as not-learned");

        AbilityUnlocks.remove(ABILITY);
        AbilityUnlocks.declare(ABILITY, (player, id) -> Optional.empty());
        assertTrue(AbilityUnlocks.isLearned(null, ABILITY).isEmpty(),
                "an abstaining provider must leave the verdict unknown");
    }

    @Test
    void nullReturnDegradesToUnknown() {
        // Contract violation (returning null, not Optional.empty) must not
        // guess and must not crash: logged once, read as unknown.
        AbilityUnlocks.declare(ABILITY, (player, id) -> null);
        assertTrue(AbilityUnlocks.isLearned(null, ABILITY).isEmpty(),
                "a null-returning provider must degrade to unknown");
        assertTrue(AbilityUnlocks.isLearned(null, ABILITY).isEmpty(),
                "repeated reads must stay unknown - the violation never flips a verdict");
    }
}
