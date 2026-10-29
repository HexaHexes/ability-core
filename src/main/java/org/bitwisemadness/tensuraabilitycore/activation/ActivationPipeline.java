package org.bitwisemadness.tensuraabilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraabilitycore.TensuraAbilityCore;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The server-authoritative activation pipeline (REQ-CORE-02).
 *
 * <p>Fixed phase order - cheap-first, contract-stable:</p>
 * <ol>
 *   <li>{@link PipelinePhase#RESOLVE} - built-in: id must exist in the core
 *       registry, else {@link ActivationOutcome#REFUSED_UNAVAILABLE}.</li>
 *   <li>{@link PipelinePhase#ADAPTER} - built-in: a handler must be
 *       registered, else {@link ActivationOutcome#REFUSED_NO_ADAPTER}.</li>
 *   <li>{@link PipelinePhase#ELIGIBILITY} - policy providers (REQ-CORE-07,
 *       core#18) register steps here; source-scoped rules live in their
 *       implementations.</li>
 *   <li>{@link PipelinePhase#COOLDOWN} - cooldown pre-check (core#16).</li>
 *   <li>{@link PipelinePhase#RESOURCE} - affordability pre-check
 *       (core#15); a refusal here means the provider is never invoked.</li>
 *   <li>{@link PipelinePhase#ACTIVATION} - built-in: the registered
 *       {@link ActivationHandler} runs the provider's native pipeline.</li>
 *   <li>{@link PipelinePhase#CHARGE} - post-success charges only
 *       (core#15's EXTERNAL model); runs solely when activation returned
 *       ACTED/PENDING. Charge-phase failures are the explicit concern of
 *       core#15 (safety wrapper lands with that issue).</li>
 * </ol>
 *
 * <p>Every step returns {@code Optional.empty()} to continue or a terminal
 * result to short-circuit the whole pipeline. The ordering guarantees from
 * the brief are structural: a refusal before ACTIVATION means the handler was
 * never invoked and CHARGE never ran (no consumption, no execution).</p>
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

    /** A registered check: empty = continue, present = terminal result. */
    @FunctionalInterface
    public interface Step {
        Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player);
    }

    /** Post-success charge action (EXTERNAL chargingModel only - ADR-002). */
    @FunctionalInterface
    public interface ChargeAction {
        void charge(ActivationRequest request, ServerPlayer player);
    }

    private final ActivationHandlerRegistry handlers;
    private final Map<PipelinePhase, List<Step>> steps = new EnumMap<>(PipelinePhase.class);
    private final List<ChargeAction> charges = new CopyOnWriteArrayList<>();

    public ActivationPipeline(ActivationHandlerRegistry handlers) {
        this.handlers = java.util.Objects.requireNonNull(handlers, "handlers");
        for (PipelinePhase phase : PipelinePhase.values()) {
            steps.put(phase, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Registers a pre-activation check. Only the pre-activation phases accept
     * steps; ACTIVATION and CHARGE are built-in (handler / charge list).
     */
    public void registerStep(PipelinePhase phase, Step step) {
        if (phase == PipelinePhase.ACTIVATION || phase == PipelinePhase.CHARGE) {
            throw new IllegalArgumentException(phase + " is a built-in phase; not open for step registration");
        }
        steps.get(phase).add(java.util.Objects.requireNonNull(step));
    }

    /** Registers a post-success charge action (runs in registration order). */
    public void registerCharge(ChargeAction action) {
        charges.add(java.util.Objects.requireNonNull(action));
    }

    /** Runs the full pipeline for one request (server-side only). */
    public ActivationResult run(ActivationRequest request, ServerPlayer player) {
        java.util.Objects.requireNonNull(request, "request");
        // player: production callers always pass the real server player; unit-test
        // fixtures may pass null (steps under test do not dereference it).

        // RESOLVE - built-in
        if (AbilityRegistries.ABILITY_REGISTRY == null
                || !AbilityRegistries.ABILITY_REGISTRY.containsKey(request.abilityId())) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        // ADAPTER - built-in
        Optional<ActivationHandler> handler = handlers.get(request.abilityId());
        if (handler.isEmpty()) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_NO_ADAPTER);
        }

        // Pre-activation phases
        for (PipelinePhase phase : new PipelinePhase[] {
                PipelinePhase.ELIGIBILITY, PipelinePhase.COOLDOWN, PipelinePhase.RESOURCE }) {
            for (Step step : steps.get(phase)) {
                Optional<ActivationResult> terminal = step.evaluate(request, player);
                if (terminal.isPresent()) {
                    return terminal.get();
                }
            }
        }

        // ACTIVATION - built-in
        ActivationResult result = handler.get().activate(request, player);
        if (result == null) {
            // handler contract violation: treat as error, never as success
            LOGGER.error("Activation handler for {} returned null", request.abilityId());
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE, "ability.error");
        }
        if (result.isRefusal()) {
            return result;
        }

        // CHARGE - built-in, only after success
        for (ChargeAction charge : charges) {
            charge.charge(request, player);
        }
        return result;
    }
}
