package org.bitwisemadness.abilitycore;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.bitwisemadness.abilitycore.activation.ActivationHandlerRegistry;
import org.bitwisemadness.abilitycore.activation.ActivationPipeline;
import org.bitwisemadness.abilitycore.activation.EligibilityChain;
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

    /** Canonical provider-adapter registry (REQ-CORE-02/03). */
    public static final ActivationHandlerRegistry HANDLERS = new ActivationHandlerRegistry();

    /** Canonical pipeline instance sharing HANDLERS (REQ-CORE-02). */
    public static final ActivationPipeline PIPELINE = new ActivationPipeline(HANDLERS);

    /** Canonical eligibility chain (REQ-CORE-07). */
    public static final EligibilityChain ELIGIBILITY = new EligibilityChain();

    public AbilityCore(IEventBus modBus) {
        modBus.addListener(AbilityRegistries::onNewRegistry);
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, TargetValidationStep.INSTANCE);
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, ELIGIBILITY.asStep());
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.COOLDOWN, ExternalCooldownStep.INSTANCE);
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);
        LOGGER.info("Ability Core initialized");
    }
}
