package org.bitwisemadness.examplemod;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Template mod entry point.
 *
 * <p>Rename {@link #MOD_ID} to match {@code mod_id} in {@code gradle.properties} as
 * part of the rename checklist in the README.</p>
 */
@Mod(ExampleMod.MOD_ID)
public final class ExampleMod {

    public static final String MOD_ID = "examplemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod(IEventBus modBus) {
        LOGGER.info("Example Mod initialized");
    }
}
