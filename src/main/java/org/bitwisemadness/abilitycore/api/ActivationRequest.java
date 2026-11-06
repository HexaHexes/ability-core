package org.bitwisemadness.abilitycore.api;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A single server-side activation attempt (REQ-CORE-02, extended by
 * REQ-CORE-06 and issue #113's mode context).
 *
 * <p>Carries <em>intent</em> only: which ability, from which source, which
 * hotbar slot (or {@link #NO_SLOT}), which mode the slot is set to (or
 * none), an advisory client timestamp (diagnostics
 * only - never authority), and a {@link TargetContext} <em>hint</em> that the
 * server resolves before providers run (core#17). Malformed wire input is
 * never built into a request - see {@link #fromWire}.</p>
 *
 * @param abilityId   registry id of the ability being activated
 * @param source      where the attempt originated (fail-closed on the wire)
 * @param slotContext hotbar slot index, or {@link #NO_SLOT} when not slot-bound
 * @param modeContext the mode the activating slot is set to (issue #113:
 *                    travels with the intent so two slots of one ability can
 *                    activate differently); advisory like the target hint -
 *                    providers validate it against their own declared modes;
 *                    null/blank = no mode preference
 * @param clientStamp advisory client time (diagnostics only)
 * @param target      targeting hint (default {@link TargetContext.None});
 *                    resolved server-side by TargetValidationStep
 */
public record ActivationRequest(
        ResourceLocation abilityId,
        ActivationSource source,
        int slotContext,
        @Nullable String modeContext,
        long clientStamp,
        TargetContext target) {

    public static final int NO_SLOT = -1;

    public ActivationRequest(ResourceLocation abilityId, ActivationSource source, int slotContext,
            long clientStamp, TargetContext target) {
        this(abilityId, source, slotContext, null, clientStamp, target);
    }

    public static ActivationRequest of(ResourceLocation abilityId, ActivationSource source) {
        return new ActivationRequest(abilityId, source, NO_SLOT, null, 0L, new TargetContext.None());
    }

    public ActivationRequest withSlot(int slot) {
        return new ActivationRequest(abilityId, source, slot, modeContext, clientStamp, target);
    }

    /**
     * The mode this activation runs under (issue #113): an advisory context
     * carried with the intent, blank rejected - omit the call for "none"
     * rather than storing an empty value.
     */
    public ActivationRequest withMode(String modeId) {
        if (modeId == null || modeId.isBlank()) {
            throw new IllegalArgumentException("modeId must not be blank - omit withMode instead");
        }
        return new ActivationRequest(abilityId, source, slotContext, modeId, clientStamp, target);
    }

    public ActivationRequest withClientStamp(long stamp) {
        return new ActivationRequest(abilityId, source, slotContext, modeContext, stamp, target);
    }

    public ActivationRequest withTarget(TargetContext newTarget) {
        return new ActivationRequest(abilityId, source, slotContext, modeContext, clientStamp,
                newTarget);
    }

    public ActivationRequest {
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(source, "source (fail-closed: malformed requests are never built)");
        Objects.requireNonNull(target, "target (use TargetContext.None, not null)");
        if (modeContext != null && modeContext.isBlank()) {
            throw new IllegalArgumentException("modeContext must not be blank when present");
        }
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
