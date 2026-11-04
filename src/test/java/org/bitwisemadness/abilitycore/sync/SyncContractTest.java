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
        // six fields now; blank hints are the wire form of "no target intent"
        var original = new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "", "");
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
                "tensura:aura_slash", "selected_slot", -1, 0L, "", "");
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
    void activatePayloadCodecRoundTripsAnEntityHint() {
        // the core#69 addition: kind + flat params ride the same codec
        var original = new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L,
                "entity", "entity=069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d");
        var buf = io.netty.buffer.Unpooled.buffer();
        try {
            ActivateAbilityPayload.STREAM_CODEC.encode(buf, original);
            var decoded = ActivateAbilityPayload.STREAM_CODEC.decode(buf);
            assertEquals(original, decoded);
            assertEquals("entity", decoded.targetKind());
            assertEquals("entity=069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d", decoded.targetParams());
        } finally {
            buf.release();
        }
    }

    @Test
    void absentTargetHintBuildsTheLegacyRequest() {
        // blank kind == pre-core#69 four-arg fromWire, byte for byte:
        // the same request object today's client would have produced
        var expected = org.bitwisemadness.abilitycore.api.ActivationRequest.fromWire(
                "tensura:fireball", "direct_slot", 3, 12345L);
        var blank = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "", ""));
        var nullKind = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, null, null));
        assertEquals(expected, blank);
        assertEquals(expected, nullKind);
    }

    @Test
    void entityTargetHintBuildsARequestCarryingTheHint() {
        var uuid = java.util.UUID.fromString("069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d");
        var request = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L,
                "entity", "entity=" + uuid));
        assertNotNull(request);
        assertEquals(new org.bitwisemadness.abilitycore.api.TargetContext.EntityHint(uuid),
                request.target());
    }

    @Test
    void declaredButUnparseableTargetDropsTheRequest() {
        // unknown kind: declared, but not in the vocabulary
        assertNull(ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "wibble", "")));
        // structurally malformed params: a pair without '='
        assertNull(ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "entity", "not-a-pair")));
        // known kind, missing required param: fromWire refuses, never guesses
        assertNull(ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "entity", "")));
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

    private static void assertNull(Object actual) {
        org.junit.jupiter.api.Assertions.assertNull(actual);
    }
}
