package org.bitwisemadness.abilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The server-authoritative activation pipeline (REQ-CORE-02, extended by
 * REQ-CORE-04).
 *
 * <p>Fixed phase order - cheap-first, contract-stable:</p>
 * <ol>
 *   <li>{@link PipelinePhase#RESOLVE} - built-in: id must exist in the core
 *       registry, else {@link ActivationOutcome#REFUSED_UNAVAILABLE}.</li>
 *   <li>{@link PipelinePhase#ADAPTER} - built-in: a declaration (handler +
 *       charging model) must exist, else {@link ActivationOutcome#REFUSED_NO_ADAPTER};
 *       the declaration is stashed in the {@link ActivationContext}.</li>
 *   <li>{@link PipelinePhase#ELIGIBILITY} - policy providers (REQ-CORE-07,
 *       core#18) register steps; source-scoped rules live in their
 *       implementations.</li>
 *   <li>{@link PipelinePhase#COOLDOWN} - cooldown pre-check (core#16).</li>
 *   <li>{@link PipelinePhase#RESOURCE} - affordability pre-check
 *       ({@code ResourceChargeStep}); a refusal here means the provider is
 *       never invoked and no charge can run. Also enforces: EXTERNAL
 *       abilities must declare a cost.</li>
 *   <li>{@link PipelinePhase#ACTIVATION} - built-in: the registered handler
 *       runs the provider's native pipeline.</li>
 *   <li>{@link PipelinePhase#CHARGE} - built-in, the single core charge site
 *       (REQ-CORE-04): for {@link ChargingModel#EXTERNAL} abilities only,
 *       calls the declared-cost resource's {@link ResourceConsumer#charge}
 *       with the validated {@link Affordability} snapshot from the context,
 *       after ACTED/PENDING. NATIVE abilities are skipped by construction.
 *       Charge failures are logged, never re-attempted, never reported as
 *       refusals (post-activation state cannot be un-done truthfully).</li>
 * </ol>
 *
 * <p>Steps share an {@link ActivationContext}. Every step returns
 * {@code Optional.empty()} to continue or a terminal result to short-circuit
 * the whole pipeline - the brief's refusal guarantees are structural.</p>
 *
 * <p>Exceptions thrown by a handler are deliberately <b>not</b> converted to
 * refusals here (see {@link ActivationHandler}): a partially-executed native
 * action reported as "nothing happened" would violate the refusal contract's
 * truthfulness. Callers catch and log with the ability id.</p>
 */
public final class ActivationPipeline {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Fixed execution order; enum ordinal is the contract. */
    public enum PipelinePhase {
        RESOLVE, ADAPTER, ELIGIBILITY, COOLDOWN, RESOURCE, ACTIVATION, CHARGE
    }

    /** Context key for the ability's charging/activation declaration. */
    public static final Object DECLARATION = new Object();

    /** A registered check: empty = continue, present = terminal result. */
    @FunctionalInterface
    public interface Step {
        Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player, ActivationContext context);
    }

    private final ActivationHandlerRegistry handlers;
    private final Map<PipelinePhase, List<Step>> steps = new EnumMap<>(PipelinePhase.class);

    public ActivationPipeline(ActivationHandlerRegistry handlers) {
        this.handlers = java.util.Objects.requireNonNull(handlers, "handlers");
        for (PipelinePhase phase : PipelinePhase.values()) {
            steps.put(phase, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Registers a pre-activation check. Only the pre-activation phases accept
     * steps; ACTIVATION and CHARGE are built-in (handler / charge routing).
     */
    public void registerStep(PipelinePhase phase, Step step) {
        if (phase == PipelinePhase.ACTIVATION || phase == PipelinePhase.CHARGE) {
            throw new IllegalArgumentException(phase + " is a built-in phase; not open for step registration");
        }
        steps.get(phase).add(java.util.Objects.requireNonNull(step));
    }

    /** Runs the full pipeline for one request (server-side only). */
    public ActivationResult run(ActivationRequest request, ServerPlayer player) {
        java.util.Objects.requireNonNull(request, "request");
        ActivationContext context = new ActivationContext(request, player);

        // RESOLVE - built-in
        var definitions = AbilityRegistries.registry();
        if (definitions == null || !definitions.containsKey(request.abilityId())) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        // ADAPTER - built-in
        Optional<ActivationHandlerRegistry.Declaration> declarationOpt =
                handlers.declaration(request.abilityId());
        if (declarationOpt.isEmpty()) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_NO_ADAPTER);
        }
        ActivationHandlerRegistry.Declaration declaration = declarationOpt.get();
        context.put(DECLARATION, declaration);

        // Pre-activation phases
        for (PipelinePhase phase : new PipelinePhase[] {
                PipelinePhase.ELIGIBILITY, PipelinePhase.COOLDOWN, PipelinePhase.RESOURCE }) {
            for (Step step : steps.get(phase)) {
                Optional<ActivationResult> terminal = step.evaluate(request, player, context);
                if (terminal.isPresent()) {
                    return terminal.get();
                }
            }
        }

        // ACTIVATION - built-in
        ActivationResult result = declaration.handler().activate(request, player);
        if (result == null) {
            // handler contract violation: treat as error, never as success
            LOGGER.error("Activation handler for {} returned null", request.abilityId());
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE, "ability.error");
        }
        if (result.isRefusal()) {
            return result;
        }

        // CHARGE - built-in: the single core charge site (EXTERNAL only)
        if (declaration.model() == ChargingModel.EXTERNAL) {
            chargeExternally(request, player, context, definitions);
        }
        // COOLDOWN start - built-in: EXTERNAL-owned cooldowns start post-activation
        // (REQ-CORE-05; NATIVE cooldowns are started by the provider's own pipeline)
        ExternalCooldownStep.startExternalCooldown(request, player);
        return result;
    }

    private void chargeExternally(ActivationRequest request, ServerPlayer player,
            ActivationContext context, net.minecraft.core.Registry<AbilityDefinition> definitions) {
        AbilityDefinition definition = definitions.get(request.abilityId());
        var cost = definition == null ? null : definition.cost();
        if (cost == null) {
            // unreachable via ResourceChargeStep (it refuses EXTERNAL-without-cost
            // before activation); defence in depth
            LOGGER.error("EXTERNAL ability {} has no declared cost - cannot charge", request.abilityId());
            return;
        }
        Affordability snapshot = context
                .get(ResourceChargeStep.AFFORDABILITY_SNAPSHOT, Affordability.class)
                .orElse(null);
        if (snapshot == null) {
            // unreachable (RESOURCE stashes it when a cost exists); defence in depth
            LOGGER.error("EXTERNAL ability {} reached charging without an affordability snapshot",
                    request.abilityId());
            return;
        }
        ResourceConsumer consumer = ResourceConsumers.get(cost.resourceId()).orElse(null);
        if (consumer == null) {
            LOGGER.error("EXTERNAL ability {} - no consumer for '{}'", request.abilityId(), cost.resourceId());
            return;
        }
        try {
            consumer.charge(request.abilityId(), player, snapshot);
        } catch (RuntimeException e) {
            // post-activation: cannot un-act; log loudly, report success truthfully
            LOGGER.error("Charging failed for {} after successful activation", request.abilityId(), e);
        }
    }
}
