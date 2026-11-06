package org.bitwisemadness.abilitycore.api;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A single server-side mode-change intent (issue #113, findings #112
 * recommendation 4): the hotbar's mode keybind fires this for the selected
 * slot's ability, core routes it to the provider's
 * {@link ModeChangeHandler}, and the provider's domain half moves its live
 * mode (ARR's native packet, for Tensura) - the consumer applies its
 * per-slot update only on ACTED, so a client write never exists.
 *
 * <p>Cycle-shaped like the upstream packet: the intent names <em>which</em>
 * ability (and which slot's setting should follow), never which mode value -
 * the provider decides the next mode from its own state. A mode change must
 * never charge, cool down, or cast; that is why this request never enters
 * the activation pipeline.</p>
 *
 * @param abilityId   the ability whose mode to change
 * @param slotContext the hotbar slot whose stored setting follows on ACTED,
 *                    or {@link ActivationRequest#NO_SLOT} when not slot-bound
 */
public record ModeChangeRequest(ResourceLocation abilityId, int slotContext) {

    public ModeChangeRequest {
        Objects.requireNonNull(abilityId, "abilityId");
        if (slotContext < ActivationRequest.NO_SLOT) {
            throw new IllegalArgumentException("slotContext must be >= "
                    + ActivationRequest.NO_SLOT + ", got " + slotContext);
        }
    }

    /**
     * Fail-closed wire entry: null for malformed input (unknown id syntax),
     * mirroring {@link ActivationRequest#fromWire} - a malformed intent is
     * never built, so the caller reports a refusal instead of guessing.
     */
    public static ModeChangeRequest fromWire(String abilityId, int slot) {
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(abilityId);
        } catch (RuntimeException e) {
            return null;
        }
        return new ModeChangeRequest(id, slot);
    }
}
