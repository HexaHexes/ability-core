package org.bitwisemadness.abilitycore.api;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The request-aware cooldown answer source for
 * {@link CooldownOwners.Owner#HANDLER} (REQ-CORE-05): core asks this
 * function pre-activation and refuses {@code REFUSED_COOLDOWN} on an
 * affirmative remaining &gt; 0 - the one pre-activation gate, asked once
 * per attempt.
 *
 * <p>Unlike {@link CooldownOwners.NativeProjection} (id + player only), the
 * handler receives the whole {@link ActivationRequest} - the seam that makes
 * per-spell and per-mode cooldowns answerable: ISS defers to each spell's
 * own cooldown (the request's slot context names the cell), Tensura to each
 * skill/magic/battlewill's per-mode cooldown (the mode context). Providers
 * whose answer cannot vary per request keep {@code declareNative} with an
 * id-level projection instead.</p>
 *
 * <p>Ownership split: core <em>checks</em> through this function but never
 * <em>starts</em> a handler cooldown - the domain starts it at its native
 * moment (the single-start rule). Answer honestly per attempt: 0 or negative
 * means ready, &gt; 0 gates; core never derives, clamps, or re-syncs the
 * value (the projection doctrine).</p>
 */
@FunctionalInterface
public interface CooldownHandler {

    /**
     * The remaining cooldown for this exact activation attempt.
     *
     * @param request the attempt being gated - ability id, slot and mode context
     * @param player  the activating player (may be null only in tests with
     *                null-safe handlers; production passes a live player)
     * @return remaining ticks; &gt; 0 refuses the attempt, 0 or negative passes
     */
    long remainingTicks(ActivationRequest request, @Nullable ServerPlayer player);
}
