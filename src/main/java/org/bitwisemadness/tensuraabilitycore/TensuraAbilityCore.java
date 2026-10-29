package org.bitwisemadness.tensuraabilitycore;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.bitwisemadness.tensuraabilitycore.activation.ActivationHandlerRegistry;
import org.bitwisemadness.tensuraabilitycore.activation.ActivationPipeline;
import org.bitwisemadness.tensuraabilitycore.activation.ResourceChargeStep;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * Tensura Ability Core entry point.
 */
@Mod(TensuraAbilityCore.MOD_ID)
public final class TensuraAbilityCore {

    public static final String MOD_ID = "tensura_ability_core";
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
     * (hotbar, bridge, menu) run requests through this instance; later Phase 2
     * issues register their steps (ELIGIBILITY/COOLDOWN/RESOURCE) and charges
     * against it at their init. Consumer-specific pipelines remain constructible
     * for isolated testing.
     */
    public static final ActivationPipeline PIPELINE = new ActivationPipeline(HANDLERS);

    public TensuraAbilityCore(IEventBus modBus) {
        modBus.addListener(AbilityRegistries::onNewRegistry);
        // REQ-CORE-04: affordability pre-check runs for every pipeline execution
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);
        LOGGER.info("Tensura Ability Core initialized");
    }
}
