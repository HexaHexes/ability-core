package org.bitwisemadness.abilitycore;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.bitwisemadness.abilitycore.activation.ActivationHandlerRegistry;
import org.bitwisemadness.abilitycore.activation.ActivationPipeline;
import org.bitwisemadness.abilitycore.activation.EligibilityChain;
import org.bitwisemadness.abilitycore.activation.ExternalCooldownStep;
import org.bitwisemadness.abilitycore.activation.ResourceChargeStep;
import org.bitwisemadness.abilitycore.activation.TargetValidationStep;
import org.bitwisemadness.abilitycore.config.CoreConfig;
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

    public AbilityCore(IEventBus modBus, ModContainer modContainer) {
        // Reference SERVER + CLIENT registration for the config contract
        // (issue #9; docs/config.md): SERVER is the authority channel, the
        // CLIENT spec simply never loads on a dedicated server.
        modContainer.registerConfig(ModConfig.Type.SERVER, CoreConfig.SERVER_SPEC, "ability_core-server.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, CoreConfig.CLIENT_SPEC, "ability_core-client.toml");
        modBus.addListener(AbilityRegistries::onNewRegistry);
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, TargetValidationStep.INSTANCE);
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, ELIGIBILITY.asStep());
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.COOLDOWN, ExternalCooldownStep.INSTANCE);
        PIPELINE.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);
        // Built-in resource pairs (issue #88): providers first, then their
        // consumers - construction-time registration per the timing contract
        BuiltinResources.registerAll();
        LOGGER.info("Ability Core initialized");
    }
}
