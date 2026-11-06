package org.bitwisemadness.abilitycore.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Provider-side mode-change adapter (issue #113, findings #112
 * recommendation 4): the {@link ActivationHandler} shape minus charging -
 * HANDLERS' contract obligations apply (run the provider's native
 * validation, return a truthful {@link ActivationResult}, do not throw),
 * with one addition specific to this intent: a mode change must <b>never</b>
 * charge, cool down, consume, or cast. It is a mode switch and nothing
 * else - which is why it rides its own registry instead of the resource
 * pipeline.
 *
 * <p>On ACTED the provider's live mode has moved; the consumer (hotbar)
 * then applies its per-slot update from the provider's own read - never on
 * a refusal.</p>
 */
@FunctionalInterface
public interface ModeChangeHandler {

    ActivationResult change(ModeChangeRequest request, ServerPlayer player);
}
