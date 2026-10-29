package org.bitwisemadness.tensuraabilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;
import org.bitwisemadness.tensuraabilitycore.api.Affordability;
import org.bitwisemadness.tensuraabilitycore.api.ChargingModel;
import org.bitwisemadness.tensuraabilitycore.api.ResourceConsumer;
import org.bitwisemadness.tensuraabilitycore.api.ResourceConsumers;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The RESOURCE-phase affordability pre-check (REQ-CORE-04, wired onto
 * {@code TensuraAbilityCore.PIPELINE} at mod construction).
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>No cost declared: NATIVE -> continue (providers enforce their own costs
 *       natively); EXTERNAL -> REFUSED_UNAFFORDABLE with
 *       {@code ability.refused.unaffordable.undeclared} (the single core charge
 *       site must have a cost to route through - fail closed pre-activation).</li>
 *   <li>Cost declared but no consumer for that resource ->
 *       {@link ActivationOutcome#REFUSED_UNAFFORDABLE} (fail closed: an
 *       ability whose payment cannot reliably be validated must not activate -
 *       the brief's REQ-BR-08 precondition; providers declaring a cost own
 *       declaring the consumer for it).</li>
 *   <li>Consumer answers INSUFFICIENT or UNKNOWN (or violates its contract by
 *       returning null) -> REFUSED_UNAFFORDABLE, nothing invoked downstream.</li>
 *   <li>SUFFICIENT -> stash the {@link Affordability} snapshot in the
 *       {@link ActivationContext} under {@link #AFFORDABILITY_SNAPSHOT} for
 *       the post-success charge action to consume.</li>
 * </ol>
 *
 * <p>Validate is contractually read-only; the snapshot handed downstream is
 * exactly what was validated (validate-then-charge, no re-derivation).</p>
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
        var definitions = AbilityRegistries.registry();
        if (definitions == null) {
            // RESOLVE already gated this in a full pipeline run; defensive for direct use
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE));
        }
        AbilityDefinition definition = definitions.get(request.abilityId());
        if (definition == null) {
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

        ResourceConsumer consumer = ResourceConsumers.get(cost.resourceId()).orElse(null);
        if (consumer == null) {
            LOGGER.debug("Refusing {}: declared resource '{}' has no consumer - payment cannot be validated",
                    request.abilityId(), cost.resourceId());
            return Optional.of(ActivationResult.refused(
                    ActivationOutcome.REFUSED_UNAFFORDABLE, "ability.refused.unaffordable.no_consumer"));
        }

        Affordability snapshot = consumer.validate(request.abilityId(), player);
        if (snapshot == null) {
            // contract violation: null is not "sufficient" - fail closed
            LOGGER.error("Resource consumer '{}' returned null for {}", cost.resourceId(), request.abilityId());
            return Optional.of(ActivationResult.refused(
                    ActivationOutcome.REFUSED_UNAFFORDABLE, "ability.refused.unaffordable.no_consumer"));
        }
        context.put(AFFORDABILITY_SNAPSHOT, snapshot);
        return switch (snapshot.state()) {
            case SUFFICIENT -> Optional.empty();
            case INSUFFICIENT, UNKNOWN -> Optional.of(
                    ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE));
        };
    }
}
