package org.bitwisemadness.abilitycore.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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
    void oversizedTargetParamsDropBeforeParsing() {
        // layer-1 code clamp (docs/config.md): past the ceiling the hint is
        // structurally garbage - the vocabulary holds uuids and numbers -
        // so the request drops instead of being buffered and parsed
        String oversized = "entity=" + "0".repeat(ActivateAbilityPayload.MAX_TARGET_PARAM_CHARS);
        assertNull(ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "entity", oversized)));
        var uuid = java.util.UUID.fromString("069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d");
        var underCeiling = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "entity", "entity=" + uuid));
        assertNotNull(underCeiling); // a real hint sits far under the ceiling
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
        // AbilitySyncNeededEvent is a simple event POJO with no unit-testable
        // fire path (it needs ServerPlayer lifecycle events): this documents
        // the type. Firing + lifecycle integration run in core#27's rows.
        assertEquals("LOGIN", AbilitySyncNeededEvent.Reason.LOGIN.name());
        assertEquals("RESPAWN", AbilitySyncNeededEvent.Reason.RESPAWN.name());
        assertEquals("DIMENSION_CHANGE", AbilitySyncNeededEvent.Reason.DIMENSION_CHANGE.name());
    }

    @Test
    void activatePayloadStaysWithinTheServerboundBudget() {
        // docs/sync.md: serverbound requests encode under 32 KiB. A generous
        // worst case (a 4 000-char id, far past any real ResourceLocation)
        // still fits with room to spare; transport frame limits bound the rest.
        var payload = new ActivateAbilityPayload(
                "tensura:" + "x".repeat(4000), "direct_slot", 18, Long.MAX_VALUE,
                "entity", "entity=069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d");
        var buf = io.netty.buffer.Unpooled.buffer();
        try {
            ActivateAbilityPayload.STREAM_CODEC.encode(buf, payload);
            assertTrue(buf.readableBytes() < 32768,
                    "encoded " + buf.readableBytes() + " bytes - over the 32 KiB budget");
        } finally {
            buf.release();
        }
    }

    @Test
    void corePayloadInventoryIsServerboundIntentOnly() throws Exception {
        // core#16 AC1 (no duplicate packet): NATIVE cooldown state is the
        // provider's to sync (ISS syncs its own - hotbar#2 section 3). A new
        // core payload must fail this list and be reviewed in docs/sync.md.
        Path mainClasses = Path.of(NetworkRegistration.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        String payloadInterface =
                "net/minecraft/network/protocol/common/custom/CustomPacketPayload";
        List<String> referencing = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainClasses)) {
            for (Path classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                String relative = mainClasses.relativize(classFile).toString().replace('\\', '/');
                if (relative.startsWith("org/bitwisemadness/abilitycore/")
                        && containsAscii(Files.readAllBytes(classFile), payloadInterface)) {
                    referencing.add(relative);
                }
            }
        }
        referencing.sort(null);
        assertEquals(List.of(
                "org/bitwisemadness/abilitycore/sync/ActivateAbilityPayload.class",
                "org/bitwisemadness/abilitycore/sync/NetworkRegistration.class"), referencing);
        // playToClient on the registrar would be a clientbound core channel -
        // native cooldown state must never ride one (core#16's no-resync rule)
        byte[] registrar = Files.readAllBytes(
                mainClasses.resolve("org/bitwisemadness/abilitycore/sync/NetworkRegistration.class"));
        assertTrue(containsAscii(registrar, "playToServer"),
                "the only core payload registers serverbound (intent)");
        assertFalse(containsAscii(registrar, "playToClient"),
                "core registers no clientbound payload - native cooldowns are never re-synced");
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

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

    private static void assertFalse(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertFalse(condition, message);
    }

    /** Constant-pool needle search (same idiom as ApiLeakGateTest). */
    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] need = needle.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - need.length; i++) {
            for (int j = 0; j < need.length; j++) {
                if (haystack[i + j] != need[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
