package org.bitwisemadness.abilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
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
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.api.ResourceProvider;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The RESOURCE-phase affordability pre-check (REQ-CORE-04, wired onto
 * {@code AbilityCore.PIPELINE} at mod construction; provider/consumer split
 * per issue #88; N-entry all-or-nothing per issue #89).
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>No costs declared (empty list): NATIVE -> continue (providers enforce
 *       their own costs natively); EXTERNAL -> REFUSED_UNAFFORDABLE with
 *       {@code ability.refused.unaffordable.undeclared} (the single core charge
 *       site must have a cost to route through - fail closed pre-activation).</li>
 *   <li>For <b>every</b> entry: no <b>provider</b> for that resource ->
 *       {@link ActivationOutcome#REFUSED_UNAFFORDABLE}
 *       ({@code ability.refused.unaffordable.no_provider}): the read half owns
 *       validation, so payment cannot be reliably validated without it.</li>
 *   <li>For every entry: provider present but no <b>consumer</b> -> same
 *       outcome with {@code ability.refused.unaffordable.no_consumer} (the
 *       write half is required to charge; providers declaring a cost own
 *       declaring both).</li>
 *   <li>For every entry: the provider's {@link ResourceProvider#check} answers
 *       INSUFFICIENT or UNKNOWN (or violates its contract by returning null)
 *       -> REFUSED_UNAFFORDABLE, nothing invoked downstream.</li>
 *   <li>All entries SUFFICIENT -> stash one {@link Affordability} snapshot
 *       <b>per entry, in list order</b>, in the {@link ActivationContext}
 *       under {@link #AFFORDABILITY_SNAPSHOT} for the post-success charge
 *       action to consume.</li>
 * </ol>
 *
 * <p><b>All-or-nothing (issue #89):</b> the full list is validated before
 * anything is stashed, so a failure at any entry charges nothing - the
 * composition rule settled on #93 follows directly: an invalid entry refuses
 * even when a {@code free} sibling is present ({@code free} never masks an
 * invalid sibling), and an all-{@code free} list is the deliberate
 * zero-cost path (each entry still validates and charges its logged
 * no-op).</p>
 *
 * <p>Each snapshot's amount is its entry's amount (the check receives it
 * - no re-derivation); the consumer's charge receives that snapshot verbatim
 * (validate-then-charge).</p>
 */
public final class ResourceChargeStep implements ActivationPipeline.Step {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceChargeStep INSTANCE = new ResourceChargeStep();

    /** Context key for the validated snapshots (one {@link Affordability} per
     * cost entry, in list order; consumed by EXTERNAL charge actions). */
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
        var costs = definition.costs();
        if (costs.isEmpty()) {
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

        // issue #89: validate the full list before stashing anything - a
        // failure at any entry charges nothing (all-or-nothing; free siblings
        // validate like every other entry, so free never masks a bad one)
        List<Affordability> snapshots = new ArrayList<>(costs.size());
        for (ResourceCost cost : costs) {
            ResourceProvider provider = ResourceProviders.get(cost.resourceId()).orElse(null);
            if (provider == null) {
                // the read half owns validation: no provider, no reliable verdict
                LOGGER.debug("Refusing {}: declared resource '{}' has no provider"
                                + " - payment cannot be validated",
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
                LOGGER.error("Resource provider '{}' returned null for {}", cost.resourceId(),
                        request.abilityId());
                return Optional.of(ActivationResult.refused(
                        ActivationOutcome.REFUSED_UNAFFORDABLE, "ability.refused.unaffordable.no_provider"));
            }
            if (snapshot.state() != Affordability.State.SUFFICIENT) {
                // names the failing entry: with N entries the resource id is
                // the only way to tell which one refused
                LOGGER.debug("Refusing {}: resource '{}' is not affordable ({})",
                        request.abilityId(), cost.resourceId(), snapshot.state());
                return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE));
            }
            snapshots.add(snapshot);
        }
        context.put(AFFORDABILITY_SNAPSHOT, List.copyOf(snapshots));
        return Optional.empty();
    }
}
