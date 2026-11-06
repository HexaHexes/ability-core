package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The mode-change intent contract (issue #113): loud handler ownership,
 * the fail-closed wire entry, and the dispatch's outcome mapping - no
 * handler refuses REFUSED_NO_ADAPTER, a handler's answer passes through
 * untouched (consumers apply their per-slot update on ACTED only), and the
 * intent never touches charging or cooldown because it never enters the
 * activation pipeline.
 */
class ModeChangeHandlersTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation UNHANDLED =
            ResourceLocation.fromNamespaceAndPath("tensura", "nobody_home");

    @AfterEach
    void isolate() {
        ModeChangeHandlers.remove(ABILITY);
    }

    @Test
    void registrationIsLoud() {
        assertThrows(IllegalArgumentException.class,
                () -> ModeChangeHandlers.register(null, (request, player) -> ActivationResult.ACTED));
        assertThrows(IllegalArgumentException.class, () -> ModeChangeHandlers.register(ABILITY, null));

        ModeChangeHandlers.register(ABILITY, (request, player) -> ActivationResult.ACTED);
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> ModeChangeHandlers.register(ABILITY, (request, player) -> ActivationResult.ACTED));
        assertTrue(duplicate.getMessage().contains(ABILITY.toString()), duplicate.getMessage());
        assertTrue(ModeChangeHandlers.has(ABILITY));
        assertTrue(ModeChangeHandlers.get(ABILITY).isPresent());
        assertTrue(ModeChangeHandlers.remove(ABILITY));
        assertTrue(ModeChangeHandlers.get(ABILITY).isEmpty());
    }

    @Test
    void theRequestIsFailClosedOnTheWire() {
        assertNull(ModeChangeRequest.fromWire("not a resource location!", 0),
                "malformed ids never become a request");
        ModeChangeRequest request = ModeChangeRequest.fromWire("tensura:fireball", 3);
        assertEquals(ABILITY, request.abilityId());
        assertEquals(3, request.slotContext());

        assertThrows(NullPointerException.class, () -> new ModeChangeRequest(null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ModeChangeRequest(ABILITY, ActivationRequest.NO_SLOT - 1));
        assertEquals(ActivationRequest.NO_SLOT,
                new ModeChangeRequest(ABILITY, ActivationRequest.NO_SLOT).slotContext());
    }

    @Test
    void dispatchMapsOutcomesWithoutGuessing() {
        assertThrows(IllegalArgumentException.class, () -> ModeChangeHandlers.run(null, null));

        // no handler: the refusal vocabulary, never a silent success
        var refused = ModeChangeHandlers.run(
                new ModeChangeRequest(UNHANDLED, ActivationRequest.NO_SLOT), null);
        assertEquals(ActivationOutcome.REFUSED_NO_ADAPTER, refused.outcome());
        assertTrue(refused.isRefusal());

        // a handler's answer passes through exactly (ACTED is the signal to
        // apply the per-slot update; a refusal means nothing moved)
        ModeChangeHandlers.register(ABILITY, (request, player) -> ActivationResult.ACTED);
        var acted = ModeChangeHandlers.run(new ModeChangeRequest(ABILITY, 4), null);
        assertEquals(ActivationOutcome.ACTED, acted.outcome());

        ModeChangeHandlers.remove(ABILITY);
        ModeChangeHandlers.register(ABILITY, (request, player) ->
                ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED));
        var restricted = ModeChangeHandlers.run(new ModeChangeRequest(ABILITY, 4), null);
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, restricted.outcome());
        assertTrue(restricted.isRefusal());
    }

    @Test
    void theIntentNeverCarriesChargingState() {
        // the intent shape itself has no cost/model surface: only the ability
        // and the slot whose setting follows on ACTED
        ModeChangeRequest request = new ModeChangeRequest(ABILITY, 2);
        assertEquals(2, request.slotContext());
        assertEquals(ABILITY, request.abilityId());
    }
}
