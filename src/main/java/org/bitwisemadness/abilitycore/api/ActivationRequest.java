package org.bitwisemadness.abilitycore.api;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A single server-side activation attempt (REQ-CORE-02, extended by
 * REQ-CORE-06).
 *
 * <p>Carries <em>intent</em> only: which ability, from which source, which
 * hotbar slot (or {@link #NO_SLOT}), an advisory client timestamp (diagnostics
 * only - never authority), and a {@link TargetContext} <em>hint</em> that the
 * server resolves before providers run (core#17). Malformed wire input is
 * never built into a request - see {@link #fromWire}.</p>
 *
 * @param abilityId   registry id of the ability being activated
 * @param source      where the attempt originated (fail-closed on the wire)
 * @param slotContext hotbar slot index, or {@link #NO_SLOT} when not slot-bound
 * @param clientStamp advisory client time (diagnostics only)
 * @param target      targeting hint (default {@link TargetContext.None});
 *                    resolved server-side by TargetValidationStep
 */
public record ActivationRequest(
        ResourceLocation abilityId,
        ActivationSource source,
        int slotContext,
        long clientStamp,
        TargetContext target) {

    public static final int NO_SLOT = -1;

    public static ActivationRequest of(ResourceLocation abilityId, ActivationSource source) {
        return new ActivationRequest(abilityId, source, NO_SLOT, 0L, new TargetContext.None());
    }

    public ActivationRequest withSlot(int slot) {
        return new ActivationRequest(abilityId, source, slot, clientStamp, target);
    }

    public ActivationRequest withClientStamp(long stamp) {
        return new ActivationRequest(abilityId, source, slotContext, stamp, target);
    }

    public ActivationRequest withTarget(TargetContext newTarget) {
        return new ActivationRequest(abilityId, source, slotContext, clientStamp, newTarget);
    }

    public ActivationRequest {
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(source, "source (fail-closed: malformed requests are never built)");
        Objects.requireNonNull(target, "target (use TargetContext.None, not null)");
        if (slotContext < NO_SLOT) {
            throw new IllegalArgumentException("slotContext must be >= " + NO_SLOT + ", got " + slotContext);
        }
    }

    /**
     * Fail-closed wire entry: returns null for malformed input (unknown id
     * syntax or unknown source) so the pipeline answers REFUSED_UNAVAILABLE
     * instead of guessing. Target hints parse separately via
     * {@link TargetContext#fromWire} - a malformed hint also yields no request
     * (never a request with a guessed target).
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
        return new ActivationRequest(id, parsed, slot, clientStamp, new TargetContext.None());
    }

    /** Wire entry with a separately parsed target hint (null target -> no request). */
    public static ActivationRequest fromWire(String abilityId, String source, int slot, long clientStamp,
            @Nullable TargetContext targetHint) {
        ActivationRequest base = fromWire(abilityId, source, slot, clientStamp);
        if (base == null) {
            return null;
        }
        if (targetHint == null) {
            return null; // a declared-but-unparseable target never becomes a default
        }
        return base.withTarget(targetHint);
    }
}
