package org.bitwisemadness.abilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.CooldownHandler;
import org.bitwisemadness.abilitycore.api.CooldownOwners;
import org.slf4j.Logger;

/**
 * The COOLDOWN-phase pre-check (REQ-CORE-05, wired onto
 * {@code AbilityCore.PIPELINE} at mod construction).
 *
 * <p>NATIVE-cooldown abilities pass straight through: their provider checks
 * cooldowns inside its own validating pipeline (verified: ISS
 * {@code canBeCastedBy} checks {@code PlayerCooldowns}; Tensura checks
 * per-skill cooldown in {@code startHoldSkill}) - core must not double-check
 * state it does not own, and must never double-start it.</p>
 *
 * <p>EXTERNAL: remaining ticks from the declared tracker; &gt; 0 ->
 * {@link ActivationOutcome#REFUSED_COOLDOWN} (pre-activation, nothing
 * consumed). A missing tracker (unconstructible via {@link CooldownOwners}
 * declarations, defence in depth) also refuses - a cooldown that cannot be
 * checked must gate the activation, not wave it through.</p>
 *
 * <p>HANDLER: remaining ticks from the declared {@link CooldownHandler}
 * with the <em>whole request</em> (slot and mode context - the per-spell,
 * per-mode seam); &gt; 0 -> {@link ActivationOutcome#REFUSED_COOLDOWN}.
 * Core never starts a handler cooldown: the domain does, at its native
 * moment (the single-start rule), so this step only gates. A missing
 * handler refuses like a missing tracker.</p>
 *
 * <p>Start timing: EXTERNAL cooldowns start <em>post-activation</em> in the
 * pipeline's built-in routing (never here - a refused activation must not
 * start a cooldown), mirroring native "start on successful cast" semantics.
 * HANDLER cooldowns never start in core at all.</p>
 */
public final class ExternalCooldownStep implements ActivationPipeline.Step {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ExternalCooldownStep INSTANCE = new ExternalCooldownStep();

    private ExternalCooldownStep() {
    }

    @Override
    public Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player,
            ActivationContext context) {
        CooldownOwners.Owner owner = CooldownOwners.ownerOf(request.abilityId());
        if (owner == CooldownOwners.Owner.HANDLER) {
            CooldownHandler handler = CooldownOwners.handlerFor(request.abilityId());
            if (handler == null) {
                LOGGER.error("HANDLER cooldown for {} has no handler - refusing", request.abilityId());
                return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_COOLDOWN));
            }
            if (handler.remainingTicks(request, player) > 0) {
                return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_COOLDOWN));
            }
            return Optional.empty();
        }
        if (owner != CooldownOwners.Owner.EXTERNAL) {
            return Optional.empty();
        }
        var tracker = CooldownOwners.trackerFor(request.abilityId()).orElse(null);
        if (tracker == null) {
            LOGGER.error("EXTERNAL cooldown for {} has no tracker - refusing", request.abilityId());
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_COOLDOWN));
        }
        long remaining = tracker.remainingTicks(request.abilityId(), player);
        if (remaining > 0) {
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_COOLDOWN));
        }
        return Optional.empty();
    }

    /**
     * Post-activation start for EXTERNAL cooldowns (invoked by the pipeline's
     * built-in routing alongside the charge site). Failures are logged, never
     * reported as refusals (post-activation truthfulness rule). HANDLER-owned
     * ids return above - core never starts those (the domain does).
     */
    public static void startExternalCooldown(ActivationRequest request, ServerPlayer player) {
        if (CooldownOwners.ownerOf(request.abilityId()) != CooldownOwners.Owner.EXTERNAL) {
            return;
        }
        var tracker = CooldownOwners.trackerFor(request.abilityId()).orElse(null);
        if (tracker == null) {
            LOGGER.error("EXTERNAL cooldown for {} cannot start - no tracker", request.abilityId());
            return;
        }
        long duration = CooldownOwners.declaredDurationTicks(request.abilityId());
        try {
            tracker.start(request.abilityId(), player, duration);
        } catch (RuntimeException e) {
            LOGGER.error("Cooldown start failed for {} after successful activation", request.abilityId(), e);
        }
    }
}
