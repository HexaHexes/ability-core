package org.bitwisemadness.abilitycore.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.junit.jupiter.api.Test;

/**
 * The mode context end-to-end (issue #113): the request's advisory mode
 * travels with the intent and survives every wither, and the payload's
 * seventh wire field (channel version 3) round-trips - blank means "no
 * mode", a malformed target still drops the whole request before any mode
 * is applied, and the pre-mode constructors keep their old behaviour.
 */
class ModeContextWireTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");

    @Test
    void modeTravelsWithTheRequestAndSurvivesWithers() {
        var base = ActivationRequest.of(ABILITY, ActivationSource.DIRECT_SLOT);
        assertNull(base.modeContext(), "no withMode call = no mode context");

        var mode = base.withMode("area");
        assertEquals("area", mode.modeContext());
        assertEquals("area", mode.withSlot(3).modeContext(), "withSlot keeps the mode");
        assertEquals("area", mode.withClientStamp(99L).modeContext(), "the stamp keeps the mode");
        assertEquals("area",
                mode.withTarget(new org.bitwisemadness.abilitycore.api.TargetContext.None())
                        .modeContext(),
                "the target keeps the mode");
        assertEquals(3, mode.withSlot(3).slotContext());
    }

    @Test
    void blankModesAreRejectedNotStored() {
        // "omit withMode" is the way to say none - a blank value would be a
        // silent no-preference pretending to be a mode
        assertThrows(IllegalArgumentException.class, () -> ActivationRequest
                .of(ABILITY, ActivationSource.DIRECT_SLOT).withMode(" "));
        assertThrows(IllegalArgumentException.class, () -> ActivationRequest
                .of(ABILITY, ActivationSource.DIRECT_SLOT).withMode(null));
    }

    @Test
    void payloadModeRoundTripsTheWire() {
        var original = new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "", "", "area");
        var buf = Unpooled.buffer();
        try {
            ActivateAbilityPayload.STREAM_CODEC.encode(buf, original);
            var decoded = ActivateAbilityPayload.STREAM_CODEC.decode(buf);
            assertEquals(original, decoded);
            assertEquals("area", decoded.mode());
        } finally {
            buf.release();
        }
        // the pre-mode constructor is the blank-mode form (additive wire growth)
        assertEquals("",
                new ActivateAbilityPayload("tensura:fireball", "direct_slot", 3, 12345L, "", "")
                        .mode());
    }

    @Test
    void blankPayloadModeBuildsNoModeContext() {
        var legacy = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "", "", ""));
        assertNotNull(legacy);
        assertNull(legacy.modeContext(), "blank wire mode = no mode context, byte-for-byte");

        var nullMode = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "", "", null));
        assertNotNull(nullMode);
        assertNull(nullMode.modeContext());
    }

    @Test
    void payloadModeBuildsTheAdvisoryContext() {
        var request = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "", "", "area"));
        assertNotNull(request);
        assertEquals("area", request.modeContext());

        // a mode rides alongside a target hint - both contexts survive
        var withTarget = ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L,
                "entity", "entity=069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d", "area"));
        assertNotNull(withTarget);
        assertEquals("area", withTarget.modeContext());
        assertEquals(new org.bitwisemadness.abilitycore.api.TargetContext.EntityHint(
                        java.util.UUID.fromString("069c2a2d-6f5a-4a4b-8f42-5f4e5a5b6c7d")),
                withTarget.target());
    }

    @Test
    void aMalformedTargetDropsTheRequestBeforeTheModeApplies() {
        // drop wins: malformed input never builds a half-request with a mode
        assertNull(ActivateAbilityPayload.toRequest(new ActivateAbilityPayload(
                "tensura:fireball", "direct_slot", 3, 12345L, "wibble", "", "area")));
    }
}
