package org.bitwisemadness.tensuraabilitycore.activation;

import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;

/**
 * Provider-side activation adapter (REQ-CORE-02, REQ-CORE-03).
 *
 * <p>Registered per ability id by the owning provider. Contract obligations:</p>
 * <ul>
 *   <li>Run the provider's <em>native</em> validating pipeline - core never
 *       re-implements upstream validation (ADR-002/ADR-005).</li>
 *   <li>Return a truthful {@link ActivationResult}: a refusal must mean
 *       nothing executed and nothing was consumed; {@code PENDING} means the
 *       native pipeline owns ongoing execution.</li>
 *   <li>Do not throw: exceptions propagate out of the pipeline and are
 *       reported by the caller as a failed activation with a logged error -
 *       the pipeline deliberately does NOT swallow them into a refusal,
 *       because a handler that already produced a side effect must never be
 *       reported as "nothing happened".</li>
 * </ul>
 */
@FunctionalInterface
public interface ActivationHandler {

    ActivationResult activate(ActivationRequest request, ServerPlayer player);
}
