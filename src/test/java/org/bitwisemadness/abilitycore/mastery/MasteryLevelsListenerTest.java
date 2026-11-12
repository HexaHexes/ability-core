package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The listener side of the datapack level table (REQ-CORE-12): the key
 * shape vanilla actually produces (folder and extension stripped - the
 * assumption the old {@code endsWith("levels.json")} filter got wrong),
 * the filter accepting only that shape, and the reload body installing
 * what the filter keeps.
 */
class MasteryLevelsListenerTest {

    private static final ResourceLocation TABLE_FILE =
            ResourceLocation.fromNamespaceAndPath("pack_a", "mastery/levels.json");
    private static final ResourceLocation STRIPPED_ID =
            ResourceLocation.fromNamespaceAndPath("pack_a", "levels");

    @AfterEach
    void isolate() {
        MasteryLevels.installForTests(null);
    }

    /**
     * The vanilla key-shape pin: {@code scanDirectory} - the exact code
     * path every {@link SimpleJsonResourceReloadListener} runs on a
     * reload - keys {@code data/<ns>/mastery/levels.json} as
     * {@code <ns>:levels}. If vanilla ever changed this contract the
     * table filter's input would change with it and this test would say
     * so; the old suite hand-built full-path keys and missed the bug.
     */
    @Test
    void scanDirectoryKeysTheTableFileStripped() {
        Map<ResourceLocation, String> fixture =
                Map.of(TABLE_FILE, "{ \"levels\": [50, 125] }");
        Map<ResourceLocation, JsonElement> collected = new LinkedHashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(
                new FixtureManager(fixture), "mastery", new Gson(), collected);

        assertEquals(Set.of(STRIPPED_ID), collected.keySet(),
                "vanilla strips mastery/ and .json before keying the map");
        assertTrue(MasteryLevelsListener.isLevelTable(STRIPPED_ID),
                "the filter must accept the key vanilla produces");
    }

    @Test
    void filterAcceptsOnlyTheStrippedTableKey() {
        assertTrue(MasteryLevelsListener.isLevelTable(STRIPPED_ID));
        assertFalse(MasteryLevelsListener.isLevelTable(
                ResourceLocation.fromNamespaceAndPath("pack_a", "mastery/levels.json")),
                "the full file name never reaches the filter - rejecting it is harmless, "
                        + "but the key shape above is the contract");
        assertFalse(MasteryLevelsListener.isLevelTable(
                ResourceLocation.fromNamespaceAndPath("pack_a", "sub/levels")),
                "only the documented path, not nested look-alikes");
        assertFalse(MasteryLevelsListener.isLevelTable(
                ResourceLocation.fromNamespaceAndPath("pack_a", "levels_extra")));
        assertFalse(MasteryLevelsListener.isLevelTable(
                ResourceLocation.fromNamespaceAndPath("pack_b", "mastery/other.json")));
    }

    @Test
    void installAppliesTheVanillaKeyedTable() {
        MasteryLevelsListener.install(
                Map.of(STRIPPED_ID, JsonParser.parseString("{ \"levels\": [50, 125] }")));

        assertTrue(MasteryLevels.hasTable(), "the vanilla-keyed table installs");
        assertEquals(0, MasteryLevels.level(49.0), "below the first threshold");
        assertEquals(1, MasteryLevels.level(50.0),
                "the table owns pacing: 50 XP is level 1 where the formula wants 100");
        assertEquals(2, MasteryLevels.level(1_000_000.0),
                "past the table the level pins at its length");
    }

    @Test
    void installSkipsForeignKeysAndBadTables() {
        MasteryLevelsListener.install(Map.of(
                ResourceLocation.fromNamespaceAndPath("pack_a", "other"),
                JsonParser.parseString("{ \"levels\": [50, 125] }")));
        assertFalse(MasteryLevels.hasTable(), "a non-table key installs nothing");

        try (LogCapture logs = LogCapture.forLogger(MasteryLevelsListener.class, Level.ERROR)) {
            MasteryLevelsListener.install(Map.of(STRIPPED_ID,
                    JsonParser.parseString("{ }")));
            assertTrue(logs.messages().stream()
                            .anyMatch(m -> m.contains("is invalid")),
                    "a bad table logs the error, got " + logs.messages());
        }
        assertFalse(MasteryLevels.hasTable(), "a bad table falls back to the formula");
    }

    /**
     * A one-directory resource manager over in-memory files: enough of
     * the vanilla contract for {@code scanDirectory} - list what sits
     * under the folder and passes the extension predicate, resolve it,
     * read it.
     */
    private static final class FixtureManager implements ResourceManager {

        private final Map<ResourceLocation, String> files;

        private FixtureManager(Map<ResourceLocation, String> files) {
            this.files = files;
        }

        @Override
        public Optional<Resource> getResource(ResourceLocation id) {
            String body = files.get(id);
            return body == null ? Optional.empty() : Optional.of(resource(body));
        }

        @Override
        public Set<String> getNamespaces() {
            return files.keySet().stream().map(ResourceLocation::getNamespace)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation id) {
            return getResource(id).map(List::of).orElse(List.of());
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String folder,
                Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, Resource> found = new LinkedHashMap<>();
            files.forEach((id, body) -> {
                if (id.getPath().startsWith(folder + "/") && filter.test(id)) {
                    found.put(id, resource(body));
                }
            });
            return found;
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String folder,
                Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, List<Resource>> found = new LinkedHashMap<>();
            listResources(folder, filter).forEach((id, res) -> found.put(id, List.of(res)));
            return found;
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }

        private static Resource resource(String body) {
            return new Resource((PackResources) null,
                    () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
