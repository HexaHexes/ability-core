package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mode ownership and the mode-read contract (issue #113): the
 * CooldownOwners mirror - one declaration per ability, core stores the
 * projection and never the state, and every unknown answers empty instead
 * of a guessed mode (REQ-HOT-08 discipline).
 */
class ModeOwnersTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("tensura", "aura_slash");

    private static final ModeOwners.NativeProjection AREA =
            (player, id) -> Optional.of("area");
    private static final ModeOwners.NativeProjection CANNOT_TELL =
            (player, id) -> null;

    @AfterEach
    void isolate() {
        ModeOwners.remove(ABILITY);
        ModeOwners.remove(OTHER);
    }

    @Test
    void ownershipIsLoudAndExclusive() {
        assertThrows(IllegalArgumentException.class, () -> ModeOwners.declareNative(null));
        assertThrows(IllegalArgumentException.class,
                () -> ModeOwners.declareNative(ABILITY, null));

        ModeOwners.declareNative(ABILITY);
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> ModeOwners.declareNative(ABILITY, AREA));
        assertTrue(duplicate.getMessage().contains(ABILITY.toString()), duplicate.getMessage());
        assertNull(ModeOwners.projectionFor(ABILITY), "declared without a projection reads none");
    }

    @Test
    void contractAnswersUnknownUntilAProjectionExists() {
        // undeclared id: unknown, never a guess
        assertTrue(ModeContract.currentMode(null, ABILITY).isEmpty());
        assertTrue(ModeContract.currentMode(null, null).isEmpty());

        // declared without a projection: still unknown (core stores no state)
        ModeOwners.declareNative(ABILITY);
        assertTrue(ModeContract.currentMode(null, ABILITY).isEmpty());

        // with a projection: the provider's answer, exactly
        ModeOwners.declareNative(OTHER, AREA);
        assertSame(AREA, ModeOwners.projectionFor(OTHER));
        assertEquals("area", ModeContract.currentMode(null, OTHER).orElseThrow());
    }

    @Test
    void aProjectionThatCannotTellAnswersUnknown() {
        ModeOwners.declareNative(ABILITY, CANNOT_TELL);
        assertTrue(ModeContract.currentMode(null, ABILITY).isEmpty(),
                "a null answer from the projection reports unknown - never a mode");
    }

    @Test
    void theSlotReadFallsBackToTheAbilityWideAnswerByDefault() {
        // a provider with one live value per ability (the common case)
        // needs no override: the slot question answers exactly what the
        // ability-wide one does, through the projection's default method
        ModeOwners.declareNative(ABILITY, AREA);
        assertEquals("area", ModeContract.currentMode(null, ABILITY, 3).orElseThrow(),
                "the default slot read delegates to the ability-wide read");
        assertTrue(ModeContract.currentMode(null, null, 3).isEmpty(),
                "a null id is unknown in either shape");
    }

    @Test
    void aPerSlotProjectionAnswersItsOwnSlot() {
        // ARR's shape: preset slots mirror the bar positionally, so the
        // projection can name the slot's own mode instead of the first
        // slot holding the ability
        ModeOwners.declareNative(ABILITY, new ModeOwners.NativeProjection() {
            @Override
            public Optional<String> modeId(ServerPlayer player, ResourceLocation id) {
                return Optional.of("first");
            }

            @Override
            public Optional<String> modeId(ServerPlayer player, ResourceLocation id, int slot) {
                return slot == 2 ? Optional.of("second") : Optional.of("first");
            }
        });

        assertEquals("second", ModeContract.currentMode(null, ABILITY, 2).orElseThrow(),
                "the per-slot answer reaches the caller that named a slot");
        assertEquals("first", ModeContract.currentMode(null, ABILITY, 0).orElseThrow(),
                "an unmapped slot falls back, never a guess");
        assertEquals("first", ModeContract.currentMode(null, ABILITY).orElseThrow(),
                "the ability-wide read is untouched by the override");
    }

    @Test
    void aPerSlotProjectionMayStillAnswerUnknown() {
        ModeOwners.declareNative(ABILITY, new ModeOwners.NativeProjection() {
            @Override
            public Optional<String> modeId(ServerPlayer player, ResourceLocation id) {
                return Optional.of("first");
            }

            @Override
            public Optional<String> modeId(ServerPlayer player, ResourceLocation id, int slot) {
                return null; // this slot: cannot tell
            }
        });

        assertTrue(ModeContract.currentMode(null, ABILITY, 7).isEmpty(),
                "a null slot answer reports unknown - never a fallback guess");
        assertEquals("first", ModeContract.currentMode(null, ABILITY).orElseThrow(),
                "the ability-wide read still answers");
    }

    @Test
    void removalRestoresTheUndeclaredState() {
        ModeOwners.declareNative(ABILITY, AREA);
        assertTrue(ModeOwners.remove(ABILITY));
        assertNull(ModeOwners.projectionFor(ABILITY));
        assertTrue(ModeContract.currentMode(null, ABILITY).isEmpty());
        assertFalse(ModeOwners.remove(ABILITY), "second removal is a no-op");
    }
}
