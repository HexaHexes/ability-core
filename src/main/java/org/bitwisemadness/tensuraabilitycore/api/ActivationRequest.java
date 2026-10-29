package org.bitwisemadness.tensuraabilitycore.api;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A single server-side activation attempt (REQ-CORE-02).
 *
 * <p>Carries <em>intent</em> only: which ability, from which source, which
 * hotbar slot (or {@link #NO_SLOT}), and an advisory client timestamp (used
 * only for logging/diagnostics - never for authority). The server resolves
 * everything else; targeting context is added by REQ-CORE-06's work (issue
 * #16) with its server-side resolution rules.</p>
 *
 * @param abilityId   registry id of the ability being activated
 * @param source      where the attempt originated; null = malformed wire input
 *                    (constructed only through {@link #of} guards)
 * @param slotContext hotbar slot index, or {@link #NO_SLOT} when not slot-bound
 * @param clientStamp advisory client time (diagnostics only)
 */
public record ActivationRequest(
        ResourceLocation abilityId,
        ActivationSource source,
        int slotContext,
        long clientStamp) {

    public static final int NO_SLOT = -1;

    public static ActivationRequest of(ResourceLocation abilityId, ActivationSource source) {
        return new ActivationRequest(abilityId, source, NO_SLOT, 0L);
    }

    public ActivationRequest withSlot(int slot) {
        return new ActivationRequest(abilityId, source, slot, clientStamp);
    }

    public ActivationRequest withClientStamp(long stamp) {
        return new ActivationRequest(abilityId, source, slotContext, stamp);
    }

    public ActivationRequest {
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(source, "source (fail-closed: malformed requests are never built)");
        if (slotContext < NO_SLOT) {
            throw new IllegalArgumentException("slotContext must be >= " + NO_SLOT + ", got " + slotContext);
        }
    }

    /**
     * Fail-closed wire entry: returns null for malformed input (unknown id
     * syntax or unknown source) so the pipeline answers REFUSED_UNAVAILABLE
     * instead of guessing.
     */
    public static ActivationRequest fromWire(String abilityId, String source, int slot, long clientStamp) {
        ResourceLocation id;
        ActivationSource parsed;
        try {
            id = ResourceLocation.parse(abilityId);
            parsed = ActivationSource.fromWire(source);
        } catch (RuntimeException e) {
            return null;
        }
        if (parsed == null) {
            return null;
        }
        return new ActivationRequest(id, parsed, slot, clientStamp);
    }
}
