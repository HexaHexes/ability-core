package org.bitwisemadness.abilitycore.activation;

import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;

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

    /**
     * The release half of one activation (key-up): end whatever the press
     * started - a held/channelled hold, a charge in progress - or do
     * nothing when the provider keeps no hold state. The default answers
     * ACTED so instant-only providers keep their single-method shape.
     *
     * <p>Contract: <b>idempotent</b> (a release with nothing held answers
     * {@link ActivationResult#ACTED}, never a refusal) and <b>never
     * policy-gated</b> - {@link ActivationPipeline#release} runs past
     * eligibility, cooldown, resource and target validation precisely so a
     * stop can never be refused and re-strand an ongoing hold (the
     * stuck-ability bug this channel exists to fix). A provider that keeps
     * holds must override this; core never models hold state itself
     * (ADR-002: the native pipeline owns execution), and releases grant no
     * mastery - the press is the award, never the key-up.</p>
     */
    default ActivationResult release(ActivationRequest request, ServerPlayer player) {
        return ActivationResult.ACTED;
    }
}
