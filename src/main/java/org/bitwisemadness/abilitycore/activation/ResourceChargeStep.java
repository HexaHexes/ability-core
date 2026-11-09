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
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.api.ResourceProvider;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The RESOURCE-phase affordability pre-check (REQ-CORE-04, wired onto
 * {@code AbilityCore.PIPELINE} at mod construction; provider/consumer split
 * per issue #88; N-entry all-or-nothing per issue #89; strategy-driven
 * selection per issue #90).
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>No costs declared (empty list): NATIVE -> continue (providers enforce
 *       their own costs natively); EXTERNAL -> REFUSED_UNAFFORDABLE with
 *       {@code ability.refused.unaffordable.undeclared} (the single core charge
 *       site must have a cost to route through - fail closed pre-activation).</li>
 *   <li><b>Price</b> the declared list through the definition's named
 *       {@link AmountResolvers.AmountResolver} (issue #103): resolution runs
 *       <b>before selection</b> so every consult observes this request's
 *       amounts. A logged contract violation - unknown or vanished name,
 *       mirror mismatch (size/order/resource id), non-finite or negative
 *       amount, a throwing resolver - refuses fail-closed with nothing
 *       charged. The built-in {@code static} resolver skips dispatch.</li>
 *   <li>Resolve the definition's named {@link ConsumptionStrategies.Strategy}
 *       (validated at registration; a name that
 *       vanished mid-session fails closed with REFUSED_UNAVAILABLE) and ask it
 *       to <b>select</b> the entries to consume (issue #90). Empty selection
 *       (nothing affordable / an in-order abort) -> REFUSED_UNAFFORDABLE,
 *       nothing charged; a null selection is a contract violation ->
 *       REFUSED_UNAFFORDABLE, logged.</li>
 *   <li>For <b>every selected</b> entry: no <b>provider</b> for that resource ->
 *       {@link ActivationOutcome#REFUSED_UNAFFORDABLE}
 *       ({@code ability.refused.unaffordable.no_provider}): the read half owns
 *       validation, so payment cannot be reliably validated without it.</li>
 *   <li>For every selected entry: provider present but no <b>consumer</b> -> same
 *       outcome with {@code ability.refused.unaffordable.no_consumer} (the
 *       write half is required to charge; providers declaring a cost own
 *       declaring both).</li>
 *   <li>For every selected entry: the provider's {@link ResourceProvider#check} answers
 *       INSUFFICIENT or UNKNOWN (or violates its contract by returning null)
 *       -> REFUSED_UNAFFORDABLE, nothing invoked downstream.</li>
 *   <li>All selected entries SUFFICIENT -> stash one {@link Affordability} snapshot
 *       <b>per entry, in the strategy's returned order</b>, in the {@link ActivationContext}
 *       under {@link #AFFORDABILITY_SNAPSHOT} for the post-success charge
 *       action to consume.</li>
 * </ol>
 *
 * <p><b>Validation scope belongs to the strategy (issue #90):</b> only the
 * selected entries are validated - a selector that skips an entry is never
 * refused for it - but everything selected must be a <em>priced</em> entry
 * from the resolved input list (an outside selection entry is a strategy
 * contract violation -> refusal)
 * and must pass fail-closed, so a failure at any selected entry charges
 * nothing (all-or-nothing, issue #89). The composition rule settled on #93
 * follows directly for the default {@code all} strategy: an invalid entry
 * refuses even when a {@code free} sibling is present - {@code free} never
 * masks an invalid sibling - and an all-{@code free} list is the deliberate
 * zero-cost path (each entry still validates and charges its logged
 * no-op).</p>
 *
 * <p>Each snapshot's amount is its (priced) entry's amount - the check
 * receives it, the resolution point never re-derives it afterwards; the
 * consumer's charge receives that snapshot verbatim (validate-then-charge).</p>
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

        // issue #90: the named strategy selects the entries to consume; core
        // validates exactly that subset fail-closed and charges it in order
        var strategy = ConsumptionStrategies.get(definition.consumptionStrategy()).orElse(null);
        if (strategy == null) {
            // validated at registration; a name that vanished is a wiring bug - fail closed
            LOGGER.error("Refusing {}: unknown consumption strategy '{}'",
                    request.abilityId(), definition.consumptionStrategy());
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE));
        }
        // issue #103: price before selection - consults and the subset below
        // see this request's amounts, never the declared nominal ones
        Optional<List<ResourceCost>> pricedOpt = price(request, player, context, definition);
        if (pricedOpt.isEmpty()) {
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE));
        }
        List<ResourceCost> priced = pricedOpt.get();
        List<ResourceCost> selected = strategy.select(request, player, priced,
                entry -> affordabilityFor(request, player, entry));
        if (selected == null) {
            // contract violation: a strategy must return a subset, never null
            LOGGER.error("Consumption strategy '{}' for {} returned null - refusing",
                    definition.consumptionStrategy(), request.abilityId());
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE));
        }
        if (selected.isEmpty()) {
            // nothing affordable (random) or an in-order abort: fail before consuming
            LOGGER.debug("Refusing {}: consumption strategy '{}' selected no entries",
                    request.abilityId(), definition.consumptionStrategy());
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE));
        }

        // validate the selected subset before stashing anything - a failure at
        // any selected entry charges nothing (all-or-nothing, issue #89; free
        // siblings validate like every other entry, so free never masks a bad one)
        List<Affordability> snapshots = new ArrayList<>(selected.size());
        for (ResourceCost cost : selected) {
            if (cost == null || !priced.contains(cost)) {
                // a strategy selects from the priced input list only - an outside
                // entry would charge something the definition never declared
                LOGGER.error("Consumption strategy '{}' for {} selected an entry outside"
                                + " the priced list {}", definition.consumptionStrategy(),
                        request.abilityId(), cost);
                return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE));
            }
            ResourceProvider provider = ResourceProviders.get(cost.resourceId()).orElse(null);
            if (provider == null || CoreConfig.resourceDisabled(cost.resourceId())) {
                // the read half owns validation: no provider, no reliable
                // verdict - and a pack-banned currency answers exactly like
                // an absent one (fail closed, same message key)
                LOGGER.debug("Refusing {}: declared resource '{}' has {}"
                                + " - payment cannot be validated",
                        request.abilityId(), cost.resourceId(),
                        provider == null ? "no provider" : "been disabled by config");
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

    /**
     * Selection-time consult: a missing provider or a contract-violating null
     * verdict reads as UNKNOWN (not affordable, fail closed). Selected entries
     * are still validated fail-closed by the loop above.
     */
    private static Affordability affordabilityFor(ActivationRequest request, ServerPlayer player,
            ResourceCost entry) {
        ResourceProvider provider = ResourceProviders.get(entry.resourceId()).orElse(null);
        if (provider == null || CoreConfig.resourceDisabled(entry.resourceId())) {
            // a banned currency reads as no provider here too: the menu's
            // consult and the charge loop can never disagree
            return Affordability.unknown(entry.resourceId());
        }
        Affordability verdict = provider.check(request.abilityId(), player, entry.amount());
        return verdict == null ? Affordability.unknown(entry.resourceId()) : verdict;
    }

    /**
     * Issue #103's resolution point: prices the declared list for this
     * request, after the empty-costs gate and before selection, so every
     * consult observes priced amounts. An empty result is a logged contract
     * violation the caller refuses fail-closed. The built-in {@code static}
     * resolver skips dispatch, pinning pre-#103 behavior byte-for-byte.
     */
    private static Optional<List<ResourceCost>> price(ActivationRequest request, ServerPlayer player,
            ActivationContext context, AbilityDefinition definition) {
        if (AmountResolvers.STATIC.equals(definition.amountResolver())) {
            return Optional.of(definition.costs());
        }
        var resolver = AmountResolvers.get(definition.amountResolver()).orElse(null);
        if (resolver == null) {
            // validated at registration; a name that vanished is a wiring bug
            LOGGER.error("Refusing {}: unknown amount resolver '{}'",
                    request.abilityId(), definition.amountResolver());
            return Optional.empty();
        }
        Object target = context.get(TargetValidationStep.RESOLVED_ENTITY, Object.class).orElse(null);
        List<ResourceCost> priced;
        try {
            priced = resolver.resolve(definition.costs(), request, player, target);
        } catch (RuntimeException e) {
            LOGGER.error("Amount resolver '{}' for {} threw - refusing",
                    definition.amountResolver(), request.abilityId(), e);
            return Optional.empty();
        }
        String violation = mirrorViolation(definition.costs(), priced);
        if (violation != null) {
            LOGGER.error("Amount resolver '{}' for {} violated its contract: {} - refusing",
                    definition.amountResolver(), request.abilityId(), violation);
            return Optional.empty();
        }
        return Optional.of(List.copyOf(priced));
    }

    /** The mirror contract (issue #103): same size, same ids in order, valid amounts. */
    private static String mirrorViolation(List<ResourceCost> declared, List<ResourceCost> priced) {
        if (priced == null) {
            return "returned null";
        }
        if (priced.size() != declared.size()) {
            return "returned " + priced.size() + " entries for " + declared.size() + " declared";
        }
        for (int i = 0; i < declared.size(); i++) {
            ResourceCost entry = priced.get(i);
            if (entry == null) {
                return "returned a null entry at index " + i;
            }
            if (!entry.resourceId().equals(declared.get(i).resourceId())) {
                return "entry " + i + " is '" + entry.resourceId() + "', expected '"
                        + declared.get(i).resourceId() + "'";
            }
            if (!Double.isFinite(entry.amount()) || entry.amount() < 0) {
                return "entry " + i + " has invalid amount " + entry.amount();
            }
        }
        return null;
    }
}
