package org.bitwisemadness.abilitycore.mastery;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.slf4j.Logger;

/**
 * The datapack reload hook for the mastery level table (REQ-CORE-12):
 * collects {@code data/<ns>/mastery/levels.json} files, validates each
 * through {@link MasteryLevels#parseLevels}, and installs via the
 * exactly-one rule ({@link MasteryLevels#applyTables}). A bad file logs
 * and is ignored (fail to the formula); the reload never fails.
 *
 * <p>The per-ability spec overlays need no listener at all - they ride
 * registry data maps and read live ({@link MasteryOverlays}).</p>
 */
@EventBusSubscriber(modid = AbilityCore.MOD_ID)
public final class MasteryLevelsListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LEVELS_SUFFIX = "levels.json";

    private MasteryLevelsListener() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "mastery") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map,
                    ResourceManager resourceManager, ProfilerFiller profiler) {
                Map<ResourceLocation, double[]> tables = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
                    // the directory collects mastery/**; only our table file qualifies
                    if (!entry.getKey().getPath().endsWith(LEVELS_SUFFIX)) {
                        continue;
                    }
                    try {
                        tables.put(entry.getKey(), MasteryLevels.parseLevels(entry.getValue()));
                    } catch (RuntimeException e) {
                        LOGGER.error("Mastery level table {} is invalid - ignored: {}",
                                entry.getKey(), e.getMessage());
                    }
                }
                MasteryLevels.applyTables(tables);
            }
        });
    }
}
