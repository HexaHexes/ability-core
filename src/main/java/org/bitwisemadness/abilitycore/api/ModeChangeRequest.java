package org.bitwisemadness.abilitycore.api;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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
 * <p>The optional <b>bar id</b> (owner's per-hotbar preset design) names
 * the hotbar bar the cycle came from, so a provider whose modes live per
 * bar can map the intent to the right store - null means the player's
 * active bar (the mode keybind's own target); client state is never
 * authoritative, the provider re-reads its own store either way.</p>
 *
 * @param abilityId   the ability whose mode to change
 * @param slotContext the hotbar slot whose stored setting follows on ACTED,
 *                    or {@link ActivationRequest#NO_SLOT} when not slot-bound
 * @param barId       the bar the cycling slot lives on, or null for active
 */
public record ModeChangeRequest(ResourceLocation abilityId, int slotContext,
        @Nullable ResourceLocation barId) {

    /**
     * The active-bar form (the mode keybind's path and the pre-bar wire
     * shape): an intent that names no bar targets the active one.
     */
    public ModeChangeRequest(ResourceLocation abilityId, int slotContext) {
        this(abilityId, slotContext, null);
    }

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
