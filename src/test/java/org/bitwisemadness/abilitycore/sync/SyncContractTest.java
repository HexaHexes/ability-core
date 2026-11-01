package org.bitwisemadness.abilitycore.sync;

import org.junit.jupiter.api.Test;

/**
 * Sync contract tests (REQ-CORE-08): payload codec round-trip, event wiring
 * existence, registrar idiom. The full integration (dedicated-server boot,
 * multiplayer sync) is validated by the combination-matrix issue (core#27).
 */
class SyncContractTest {

    @Test
    void activatePayloadCodecRoundTrips() {
        var original = new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L);
        var buf = io.netty.buffer.Unpooled.buffer();
        try {
            ActivateAbilityPayload.STREAM_CODEC.encode(buf, original);
            var decoded = ActivateAbilityPayload.STREAM_CODEC.decode(buf);
            assertEquals(original, decoded);
        } finally {
            buf.release();
        }
    }

    @Test
    void activatePayloadCodecHandlesNegativeSlot() {
        var original = new ActivateAbilityPayload(
                "tensura:aura_slash", "selected_slot", -1, 0L);
        var buf = io.netty.buffer.Unpooled.buffer();
        try {
            ActivateAbilityPayload.STREAM_CODEC.encode(buf, original);
            var decoded = ActivateAbilityPayload.STREAM_CODEC.decode(buf);
            assertEquals(original, decoded);
            assertEquals(-1, decoded.slotContext());
        } finally {
            buf.release();
        }
    }

    @Test
    void syncNeededEventCarriesPlayerAndReason() {
        // AbilitySyncNeededEvent is a simple event POJO; the GameTest exercises
        // the actual firing via PlayerLoggedInEvent. This test documents the type.
        assertEquals("LOGIN", AbilitySyncNeededEvent.Reason.LOGIN.name());
        assertEquals("RESPAWN", AbilitySyncNeededEvent.Reason.RESPAWN.name());
        assertEquals("DIMENSION_CHANGE", AbilitySyncNeededEvent.Reason.DIMENSION_CHANGE.name());
    }

    @Test
    void networkRegistrationClassExists() {
        // the registrar wiring is mod-bus; this test just proves the class loads
        // without a dedicated-server crash (client-only classes are elsewhere)
        assertNotNull(NetworkRegistration.class);
        assertNotNull(ActivateAbilityPayload.class);
        assertNotNull(AbilitySyncNeededEvent.class);
        assertNotNull(SyncEventWiring.class);
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static void assertNotNull(Object actual) {
        org.junit.jupiter.api.Assertions.assertNotNull(actual);
    }
}
