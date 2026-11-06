package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The slot gate (issue #87): fail-closed on a missing cell (never an
 * "active slot" fallback), stale/out-of-range cells judged against the
 * caller's <b>live</b> count, and the documented outcome vocabulary -
 * both failures refuse {@code REFUSED_UNAVAILABLE}, mirroring
 * pseudocore-curios' verified press handler.
 */
class ActivationRequestsTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "slot_gate_probe");

    private static ActivationRequest withSlot(int slot) {
        return ActivationRequest.of(ABILITY, ActivationSource.DIRECT_SLOT).withSlot(slot);
    }

    @Test
    void missingSlotContextRefusesFailClosed() {
        var gate = ActivationRequests.requireSlot(
                ActivationRequest.of(ABILITY, ActivationSource.DIRECT_SLOT), 9);

        assertFalse(gate.isValid(), "NO_SLOT must never guess a target");
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, gate.refusal().outcome());
    }

    @Test
    void outOfRangeAndStaleCellsRefuseAgainstTheLiveCount() {
        // a stale seeded cell (index 5) against today's 3-slot bar: refused,
        // never remapped onto a neighbour
        var stale = ActivationRequests.requireSlot(withSlot(5), 3);
        assertFalse(stale.isValid());
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, stale.refusal().outcome());

        var wayPast = ActivationRequests.requireSlot(withSlot(9), 3);
        assertFalse(wayPast.isValid());

        var emptyBar = ActivationRequests.requireSlot(withSlot(0), 0);
        assertFalse(emptyBar.isValid(), "an empty live bar refuses every cell");
    }

    @Test
    void bothFailuresShareTheDocumentedOutcome() {
        // the press handler's NO_SLOT fold: missing and range/stale are the
        // same outcome - callers return it verbatim, no re-derivation
        var missing = ActivationRequests.requireSlot(
                ActivationRequest.of(ABILITY, ActivationSource.DIRECT_SLOT), 3);
        var range = ActivationRequests.requireSlot(withSlot(7), 3);

        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, missing.refusal().outcome());
        assertEquals(missing.refusal().outcome(), range.refusal().outcome());
    }

    @Test
    void inRangePassesThroughTheCallersLogic() {
        var first = ActivationRequests.requireSlot(withSlot(0), 3);
        assertTrue(first.isValid());
        assertEquals(0, first.slot());

        var last = ActivationRequests.requireSlot(withSlot(2), 3);
        assertTrue(last.isValid(), "the last live cell is in range");
        assertEquals(2, last.slot());
    }

    @Test
    void callerLogicRunsOnlyOnAValidGate() {
        // the handler shape from the issue: refusal returned as-is, logic
        // otherwise - nothing acts when the gate refused
        boolean acted = false;

        var refused = ActivationRequests.requireSlot(withSlot(4), 4);
        if (refused.isValid()) {
            acted = true;
        }
        assertFalse(acted, "an out-of-range cell never reaches the caller's logic");

        var valid = ActivationRequests.requireSlot(withSlot(3), 4);
        if (valid.isValid()) {
            acted = true;
        }
        assertTrue(acted, "an in-range cell passes through");
        assertEquals(3, valid.slot());
    }
}
