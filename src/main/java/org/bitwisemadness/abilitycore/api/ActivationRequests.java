package org.bitwisemadness.abilitycore.api;

import org.jetbrains.annotations.Nullable;

/**
 * Request-level gates shared by slot-dispatched ability handlers (issue #87).
 *
 * <p>Abilities that act on a specific cell of a content-owned bar all need
 * the same guard, and it is wire-level semantics rather than domain logic:
 * read {@link ActivationRequest#slotContext()}, refuse <b>fail-closed</b>
 * when it is absent (a request without a cell must never guess which target
 * is meant - there is deliberately <b>no "active slot" fallback</b>), and
 * range-check against the <em>live</em> slot count so a stale seeded cell
 * refuses instead of acting on a neighbour. The handlers keep their own
 * domain refusals (cooldown, gating, ...) and only consume the validated
 * index.</p>
 *
 * <p><b>Outcome vocabulary</b> (mirrors pseudocore-curios' {@code press}
 * handler, verified on its main): a missing {@code slotContext} refuses
 * {@code REFUSED_UNAVAILABLE}; an out-of-range or stale cell
 * ({@code slot >= slotCount}) refuses {@code REFUSED_UNAVAILABLE} too - that
 * handler folds its range check into the same NO_SLOT refusal. Usable from
 * {@code ChargingModel.NATIVE} handlers; carries no upstream-mod types.</p>
 */
public final class ActivationRequests {

    private ActivationRequests() {
    }

    /**
     * Validates the request's slot against the live slot count.
     *
     * @param request   the activation request
     * @param slotCount the caller's <b>live</b> slot count (re-read per call -
     *                  a stale seed must be judged against today's bar)
     * @return either the validated index ({@link SlotGate#isValid()}) or the
     *         refusal outcome to return from the handler
     */
    public static SlotGate requireSlot(ActivationRequest request, int slotCount) {
        int slot = request.slotContext();
        if (slot < 0 || slot >= slotCount) {
            // missing context and stale/out-of-range both fail closed with
            // the same outcome (the press handler's NO_SLOT fold)
            return SlotGate.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        return SlotGate.valid(slot);
    }

    /**
     * Either the validated slot index ({@link #isValid()}) or the
     * {@link ActivationResult} the handler returns unchanged. One component
     * is always null - the tagged pair keeps the refusal's outcome travelling
     * with the gate instead of forcing callers to re-derive it.
     *
     * @param slot     the validated index when {@code refusal} is null
     * @param refusal  the refusal to return when the gate failed
     */
    public record SlotGate(int slot, @Nullable ActivationResult refusal) {

        private static SlotGate valid(int slot) {
            return new SlotGate(slot, null);
        }

        private static SlotGate refused(ActivationOutcome outcome) {
            return new SlotGate(ActivationRequest.NO_SLOT, ActivationResult.refused(outcome));
        }

        /** @return true when {@link #slot()} carries a validated index. */
        public boolean isValid() {
            return refusal == null;
        }
    }
}
