package org.bitwisemadness.abilitycore.config;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.slf4j.Logger;

/**
 * Wires the loud file inspection into core's own config lifecycle
 * (REQ-CFG-03, issue #10): every {@code ModConfigEvent.Loading} /
 * {@code Reloading} of a core spec re-reads the file on disk - the
 * operator's original, since NeoForge corrects only the in-memory model -
 * and logs unknown keys and corrected values <b>with their key names</b>.
 *
 * <p>Consumers copy this one-liner wiring for their own specs (see
 * {@code docs/config.md}). The handler never throws: an inspection problem
 * must never fail a config load.</p>
 */
@EventBusSubscriber(modid = AbilityCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ConfigInspection {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfigInspection() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        inspect(event);
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        inspect(event);
    }

    private static void inspect(ModConfigEvent event) {
        try {
            ModConfig config = event.getConfig();
            IConfigSpec registered = config.getSpec();
            ModConfigSpec spec = registered == CoreConfig.SERVER_SPEC ? CoreConfig.SERVER_SPEC
                    : registered == CoreConfig.CLIENT_SPEC ? CoreConfig.CLIENT_SPEC : null;
            if (spec == null) {
                return; // not a core spec (shared mod bus, foreign configs)
            }
            ConfigDiagnostics.inspectFile(spec, config.getFullPath(), config.getFileName());
        } catch (RuntimeException e) {
            LOGGER.warn("Config inspection failed for {}", event.getConfig().getFileName(), e);
        }
    }
}
