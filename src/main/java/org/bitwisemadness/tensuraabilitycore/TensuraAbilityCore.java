package org.bitwisemadness.tensuraabilitycore;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Tensura Ability Core entry point.
 */
@Mod(TensuraAbilityCore.MOD_ID)
public final class TensuraAbilityCore {

    public static final String MOD_ID = "tensura_ability_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraAbilityCore(IEventBus modBus) {
        LOGGER.info("Tensura Ability Core initialized");
    }
}