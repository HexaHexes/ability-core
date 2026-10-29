package org.bitwisemadness.tensuraabilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;
import org.bitwisemadness.tensuraabilitycore.api.CooldownOwners;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The COOLDOWN-phase pre-check (REQ-CORE-05, wired onto
 * {@code TensuraAbilityCore.PIPELINE} at mod construction).
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
 * <p>Start timing: EXTERNAL cooldowns start <em>post-activation</em> in the
 * pipeline's built-in routing (never here - a refused activation must not
 * start a cooldown), mirroring native "start on successful cast" semantics.</p>
 */
public final class ExternalCooldownStep implements ActivationPipeline.Step {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ExternalCooldownStep INSTANCE = new ExternalCooldownStep();

    private ExternalCooldownStep() {
    }

    @Override
    public Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player,
            ActivationContext context) {
        if (CooldownOwners.ownerOf(request.abilityId()) != CooldownOwners.Owner.EXTERNAL) {
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
     * reported as refusals (post-activation truthfulness rule).
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
