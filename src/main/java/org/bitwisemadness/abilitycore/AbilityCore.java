package org.bitwisemadness.abilitycore;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.bitwisemadness.abilitycore.activation.ActivationHandlerRegistry;
import org.bitwisemadness.abilitycore.activation.ActivationPipeline;
import org.bitwisemadness.abilitycore.activation.ExternalCooldownStep;
import org.bitwisemadness.abilitycore.activation.ResourceChargeStep;
import org.bitwisemadness.abilitycore.activation.TargetValidationStep;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * Ability Core entry point.
 */
@Mod(AbilityCore.MOD_ID)
public final class AbilityCore {

    public static final String MOD_ID = "ability_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The canonical provider-adapter registry: providers register per-ability
     * handlers here during their mod setup (REQ-CORE-02/03). Instance fields
     * (not class-init constants) so construction happens with the mod
     * container present and tests can observe a consistent lifecycle.
     */
    public static final ActivationHandlerRegistry HANDLERS = new ActivationHandlerRegistry();

    /**
     * The canonical pipeline instance sharing {@link #HANDLERS}. Consumers
     * (consumers) run requests through this instance; later Phase 2
     * issues register their steps (ELIGIBILITY/COOLDOWN/RESOURCE) and charges
     * against it at their init. Consumer-specific pipelines remain constructible
     * for isolated testing.
     */
    public static final ActivationPipeline PIPELINE = new ActivationPipeline(HANDLERS);

    public AbilityCore(IEventBus modBus) {
        modBus.addListener(AbilityRegistries::onNewRegistry);
        // REQ-CORE-04: affordability pre-check runs for every pipeline execution
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);
        // REQ-CORE-06: target hints resolve first - provider eligibility rules
        // (registered later) run against resolved targets, not raw hints
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, TargetValidationStep.INSTANCE);
        // REQ-CORE-05: EXTERNAL-cooldown gate runs after charge pre-validation,
        // before activation; EXTERNAL starts happen post-activation (built-in)
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.COOLDOWN, ExternalCooldownStep.INSTANCE);
        LOGGER.info("Ability Core initialized");
    }
}
