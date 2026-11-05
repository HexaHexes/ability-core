package org.bitwisemadness.abilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceProvider;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The RESOURCE-phase affordability pre-check (REQ-CORE-04, wired onto
 * {@code AbilityCore.PIPELINE} at mod construction; provider/consumer split
 * per issue #88).
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>No cost declared: NATIVE -> continue (providers enforce their own costs
 *       natively); EXTERNAL -> REFUSED_UNAFFORDABLE with
 *       {@code ability.refused.unaffordable.undeclared} (the single core charge
 *       site must have a cost to route through - fail closed pre-activation).</li>
 *   <li>Cost declared but no <b>provider</b> for that resource ->
 *       {@link ActivationOutcome#REFUSED_UNAFFORDABLE}
 *       ({@code ability.refused.unaffordable.no_provider}): the read half owns
 *       validation, so payment cannot be reliably validated without it.</li>
 *   <li>Provider present but no <b>consumer</b> -> same outcome with
 *       {@code ability.refused.unaffordable.no_consumer} (the write half is
 *       required to charge; providers declaring a cost own declaring both).</li>
 *   <li>The provider's {@link ResourceProvider#check} answers INSUFFICIENT or
 *       UNKNOWN (or violates its contract by returning null) ->
 *       REFUSED_UNAFFORDABLE, nothing invoked downstream.</li>
 *   <li>SUFFICIENT -> stash the {@link Affordability} snapshot in the
 *       {@link ActivationContext} under {@link #AFFORDABILITY_SNAPSHOT} for
 *       the post-success charge action to consume.</li>
 * </ol>
 *
 * <p>The snapshot's amount is the cost entry's amount (the check receives it
 * - no re-derivation); the consumer's charge receives that snapshot verbatim
 * (validate-then-charge).</p>
 */
public final class ResourceChargeStep implements ActivationPipeline.Step {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceChargeStep INSTANCE = new ResourceChargeStep();

    /** Context key for the validated snapshot (consumed by EXTERNAL charge actions). */
    public static final Object AFFORDABILITY_SNAPSHOT = new Object();

    private ResourceChargeStep() {
    }

    @Override
    public Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player,
            ActivationContext context) {
        // overlay-merged lookup: a datapack-adjusted cost is what gets validated
        AbilityDefinition definition = AbilityRegistries.definition(request.abilityId());
        if (definition == null) {
            // RESOLVE already gated this in a full pipeline run; defensive for direct use
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE));
        }
        var cost = definition.cost();
        if (cost == null) {
            // REQ-CORE-04: the single core charge site needs a declared cost to
            // route through - EXTERNAL without one is refused pre-activation.
            var declaration = context.get(ActivationPipeline.DECLARATION,
                    ActivationHandlerRegistry.Declaration.class).orElse(null);
            if (declaration != null && declaration.model() == ChargingModel.EXTERNAL) {
                LOGGER.debug("Refusing {}: EXTERNAL chargingModel without a declared cost", request.abilityId());
                return Optional.of(ActivationResult.refused(
                        ActivationOutcome.REFUSED_UNAFFORDABLE, "ability.refused.unaffordable.undeclared"));
            }
            return Optional.empty();
        }

        ResourceProvider provider = ResourceProviders.get(cost.resourceId()).orElse(null);
        if (provider == null) {
            // the read half owns validation: no provider, no reliable verdict
            LOGGER.debug("Refusing {}: declared resource '{}' has no provider - payment cannot be validated",
                    request.abilityId(), cost.resourceId());
            return Optional.of(ActivationResult.refused(
                    ActivationOutcome.REFUSED_UNAFFORDABLE, "ability.refused.unaffordable.no_provider"));
        }

        ResourceConsumer consumer = ResourceConsumers.get(cost.resourceId()).orElse(null);
        if (consumer == null) {
            LOGGER.debug("Refusing {}: declared resource '{}' has no consumer - it cannot be charged",
                    request.abilityId(), cost.resourceId());
            return Optional.of(ActivationResult.refused(
                    ActivationOutcome.REFUSED_UNAFFORDABLE, "ability.refused.unaffordable.no_consumer"));
        }

        Affordability snapshot = provider.check(request.abilityId(), player, cost.amount());
        if (snapshot == null) {
            // contract violation: null is not "sufficient" - fail closed
            LOGGER.error("Resource provider '{}' returned null for {}", cost.resourceId(), request.abilityId());
            return Optional.of(ActivationResult.refused(
                    ActivationOutcome.REFUSED_UNAFFORDABLE, "ability.refused.unaffordable.no_provider"));
        }
        context.put(AFFORDABILITY_SNAPSHOT, snapshot);
        return switch (snapshot.state()) {
            case SUFFICIENT -> Optional.empty();
            case INSUFFICIENT, UNKNOWN -> Optional.of(
                    ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE));
        };
    }
}
