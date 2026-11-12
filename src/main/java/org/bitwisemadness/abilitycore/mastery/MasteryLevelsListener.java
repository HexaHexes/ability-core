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
 * <p>Vanilla's {@link SimpleJsonResourceReloadListener#scanDirectory}
 * keys every collected file through {@code FileToIdConverter.fileToId},
 * which strips the {@code mastery/} folder and the {@code .json}
 * extension - the documented file arrives as {@code <ns>:levels}, and
 * {@link #isLevelTable} matches exactly that shape.</p>
 *
 * <p>The per-ability spec overlays need no listener at all - they ride
 * registry data maps and read live ({@link MasteryOverlays}).</p>
 */
@EventBusSubscriber(modid = AbilityCore.MOD_ID)
public final class MasteryLevelsListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LEVELS_PATH = "levels";

    private MasteryLevelsListener() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "mastery") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map,
                    ResourceManager resourceManager, ProfilerFiller profiler) {
                install(map);
            }
        });
    }

    /**
     * Whether a collected id is the documented table file: vanilla strips
     * the folder and extension, so {@code data/<ns>/mastery/levels.json}
     * keys as {@code <ns>:levels} - never as the file name.
     *
     * @param id a key as produced by vanilla's scanDirectory
     * @return true when the id names the mastery level table
     */
    static boolean isLevelTable(ResourceLocation id) {
        return id.getPath().equals(LEVELS_PATH);
    }

    /**
     * The reload body: keep the collected files that name the table,
     * parse each (an invalid one logs and is skipped), and hand the
     * survivors to the exactly-one install rule - zero qualifying files
     * fall back to the config formula.
     *
     * @param map vanilla-keyed files from scanDirectory with their JSON
     */
    static void install(Map<ResourceLocation, JsonElement> map) {
        Map<ResourceLocation, double[]> tables = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            if (!isLevelTable(entry.getKey())) {
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
}
