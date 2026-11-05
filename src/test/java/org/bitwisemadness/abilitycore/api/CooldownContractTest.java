package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The cooldown check interface (REQ-CORE-05, core#16 AC1/AC3): NATIVE parity -
 * core reports the provider's own remaining exactly, through a stored function
 * never a stored copy - unknown stays unknown, EXTERNAL reads the same tracker
 * the gate reads, and declarations stay exclusive and validated.
 */
class CooldownContractTest {

    private static final ResourceLocation NATIVE_PROJECTED =
            ResourceLocation.fromNamespaceAndPath("tensura", "native_projected");
    private static final ResourceLocation NATIVE_PLAIN =
            ResourceLocation.fromNamespaceAndPath("tensura", "native_plain");
    private static final ResourceLocation EXTERNAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "external_cd");
    private static final ResourceLocation NEVER_DECLARED =
            ResourceLocation.fromNamespaceAndPath("tensura", "never_declared");

    /** The provider's own native store - the only truth the projection reads. */
    private long nativeRemaining = 0L;
    private long stubTrackerRemaining = 0L;

    @BeforeEach
    void declarationsClean() {
        nativeRemaining = 0L;
        stubTrackerRemaining = 0L;
    }

    @AfterEach
    void tearDown() {
        CooldownOwners.remove(NATIVE_PROJECTED);
        CooldownOwners.remove(NATIVE_PLAIN);
        CooldownOwners.remove(EXTERNAL);
    }

    private CooldownTracker stubTracker() {
        return new CooldownTracker() {
            @Override
            public long remainingTicks(ResourceLocation abilityId,
                    net.minecraft.server.level.ServerPlayer player) {
                return stubTrackerRemaining;
            }

            @Override
            public void start(ResourceLocation abilityId, net.minecraft.server.level.ServerPlayer player,
                    long durationTicks) {
                stubTrackerRemaining = durationTicks;
            }
        };
    }

    @Test
    void nativeProjectionReportsTheProvidersRemainingExactly() {
        // AC1 parity: core's answer IS the provider's native value - the store
        // is read through on every call, so there is no copy to drift and no
        // core-side state that a packet could ever duplicate.
        CooldownOwners.declareNative(NATIVE_PROJECTED, (id, player) -> nativeRemaining);

        nativeRemaining = 40L;
        assertEquals(Optional.of(Duration.ofMillis(40L * 50L)),
                CooldownContract.remaining(NATIVE_PROJECTED, null),
                "core must report the provider's native remaining, exactly");
        assertTrue(CooldownContract.isOnCooldown(NATIVE_PROJECTED, null));

        nativeRemaining = 7L; // live read: the next query reflects the provider's now
        assertEquals(Optional.of(Duration.ofMillis(7L * 50L)),
                CooldownContract.remaining(NATIVE_PROJECTED, null));

        nativeRemaining = 0L;
        assertEquals(Optional.of(Duration.ZERO),
                CooldownContract.remaining(NATIVE_PROJECTED, null));
        assertFalse(CooldownContract.isOnCooldown(NATIVE_PROJECTED, null));

        assertTrue(CooldownOwners.trackerFor(NATIVE_PROJECTED).isEmpty(),
                "no core tracker exists for a NATIVE ability - nothing to sync");
        assertEquals(CooldownOwners.Owner.NATIVE, CooldownOwners.ownerOf(NATIVE_PROJECTED),
                "the projection never changes ownership");
    }

    @Test
    void nativeWithoutAProjectionReadsUnknownNeverReady() {
        CooldownOwners.declareNative(NATIVE_PLAIN);

        assertTrue(CooldownContract.remaining(NATIVE_PLAIN, null).isEmpty(),
                "core cannot know a NATIVE cooldown it was not given - unknown, not ready");
        assertFalse(CooldownContract.isOnCooldown(NATIVE_PLAIN, null),
                "unknown must not be claimed as a cooldown either");
        assertTrue(CooldownContract.remaining(NEVER_DECLARED, null).isEmpty(),
                "undeclared ids fail closed to NATIVE (ownerOf default) -> unknown");
    }

    @Test
    void externalAnswersComeFromTheSameTrackerTheGateReads() {
        CooldownOwners.declareExternal(EXTERNAL, 100L, stubTracker());

        stubTrackerRemaining = 25L;
        assertEquals(Optional.of(Duration.ofMillis(25L * 50L)),
                CooldownContract.remaining(EXTERNAL, null),
                "the contract and the pipeline gate share one answer source");
        assertTrue(CooldownContract.isOnCooldown(EXTERNAL, null));

        stubTrackerRemaining = 0L;
        assertEquals(Optional.of(Duration.ZERO), CooldownContract.remaining(EXTERNAL, null));
        assertFalse(CooldownContract.isOnCooldown(EXTERNAL, null));
        assertNull(CooldownOwners.projectionFor(EXTERNAL),
                "EXTERNAL answers from its tracker - never from a projection");
    }

    @Test
    void projectionDeclarationsAreValidatedAndExclusive() {
        assertThrows(IllegalArgumentException.class,
                () -> CooldownOwners.declareNative(NATIVE_PROJECTED, null),
                "a null projection must fail loudly - use the no-projection form");

        CooldownOwners.declareNative(NATIVE_PLAIN, (id, player) -> 0L);
        assertThrows(IllegalStateException.class,
                () -> CooldownOwners.declareNative(NATIVE_PLAIN, (id, player) -> 1L),
                "duplicate declaration fails - one owner per ability (AC3)");
        assertThrows(IllegalStateException.class,
                () -> CooldownOwners.declareExternal(NATIVE_PLAIN, 100L, stubTracker()),
                "dual-owner declaration fails even in projection form (AC3)");

        assertTrue(CooldownOwners.remove(NATIVE_PLAIN), "redeclaration after remove is allowed");
        CooldownOwners.declareNative(NATIVE_PLAIN);
        assertNull(CooldownOwners.projectionFor(NATIVE_PLAIN), "no projection declared -> none found");
    }

    @Test
    void nullAndNegativeInputsAreFailClosed() {
        assertTrue(CooldownContract.remaining(null, null).isEmpty());
        assertFalse(CooldownContract.isOnCooldown(null, null));
        assertNull(CooldownOwners.projectionFor(null));

        // a source reporting garbage (negative) clamps to zero: ready at worst,
        // never a negative Duration and never a disagreement with the gate
        CooldownOwners.declareNative(NATIVE_PROJECTED, (id, player) -> -5L);
        assertEquals(Optional.of(Duration.ZERO), CooldownContract.remaining(NATIVE_PROJECTED, null));
    }
}
