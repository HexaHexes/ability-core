package org.bitwisemadness.abilitycore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The loud, read-only file inspection (issue #10 AC1 + AC4): a config file
 * holding an unknown key and an out-of-range value gets <b>both logged with
 * their key names</b> while the defaults apply to the in-memory copy - and
 * the file on disk is never rewritten or deleted, so hand-annotations and
 * user edits survive.
 */
class ConfigInspectionTest {

    @TempDir
    Path dir;

    private static ModConfigSpec rangedSpec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("diagnostics");
        builder.defineInRange("max", 10, 1, 18);
        builder.pop();
        return builder.build();
    }

    private static boolean anyContains(List<String> messages, String needle) {
        return messages.stream().anyMatch(line -> line.contains(needle));
    }

    @Test
    void invalidAndUnknownKeysAreBothLoggedWithKeyNames() throws IOException {
        Path file = dir.resolve("inspection-server.toml");
        String original = "[diagnostics]" + System.lineSeparator()
                + "max = 999" + System.lineSeparator()
                + "# operator note: tuned for the arena" + System.lineSeparator()
                + "unknownFlag = true" + System.lineSeparator();
        Files.writeString(file, original, StandardCharsets.UTF_8);

        try (LogCapture logs = LogCapture.forLogger(ConfigDiagnostics.class, Level.WARN)) {
            ConfigDiagnostics.inspectFile(rangedSpec(), file, "inspection-server.toml");

            List<String> messages = logs.messages();
            assertTrue(anyContains(messages, "diagnostics.max"),
                    "the out-of-range value must be logged with its key name: " + messages);
            assertTrue(anyContains(messages, "999"),
                    "the log must name the offending value: " + messages);
            assertTrue(anyContains(messages, "diagnostics.unknownFlag"),
                    "the unknown key must be logged with its key name: " + messages);
        }
    }

    @Test
    void inspectionNeverTouchesTheFileOnDisk() throws IOException {
        Path file = dir.resolve("readonly-server.toml");
        String original = "[diagnostics]" + System.lineSeparator()
                + "max = 999" + System.lineSeparator()
                + "unknownFlag = true" + System.lineSeparator();
        Files.writeString(file, original, StandardCharsets.UTF_8);

        ConfigDiagnostics.inspectFile(rangedSpec(), file, "readonly-server.toml");

        assertTrue(Files.exists(file), "a config file is never deleted");
        assertEquals(original, Files.readString(file, StandardCharsets.UTF_8),
                "inspection is read-only: no correction, no rewrite, unknown keys intact");
    }

    @Test
    void correctionsApplyThePlatformRuleToTheParsedCopy() {
        // Ground truth (verified against NeoForge's correction): out-of-range
        // values are CLAMPED into range, unreadable ones reset to the default.
        // The inspection observes that rule; it never rewrites the file.
        try (LogCapture logs = LogCapture.forLogger(ConfigDiagnostics.class, Level.WARN)) {
            CommentedConfig ranged = new TomlParser()
                    .parse(new StringReader("[diagnostics]" + System.lineSeparator() + "max = 999"));
            assertTrue(ConfigDiagnostics.correctAndLog(rangedSpec(), ranged, "unit.toml") >= 1,
                    "the out-of-range value must be corrected");
            assertEquals(18, intValue(ranged, "diagnostics.max"),
                    "an out-of-range value clamps into range (max), not to the default");

            CommentedConfig garbled = new TomlParser()
                    .parse(new StringReader("[diagnostics]" + System.lineSeparator() + "max = \"abc\""));
            ConfigDiagnostics.correctAndLog(rangedSpec(), garbled, "unit.toml");
            assertEquals(10, intValue(garbled, "diagnostics.max"),
                    "an unreadable value resets to the default");

            List<String> messages = logs.messages();
            assertTrue(anyContains(messages, "diagnostics.max"),
                    "the correction must be logged with its key name: " + messages);
            assertTrue(anyContains(messages, "999"),
                    "the log must name the offending value: " + messages);
        }
    }

    private static int intValue(CommentedConfig raw, String path) {
        Object value = raw.get(path);
        assertTrue(value instanceof Number, path + " should hold a number, got " + value);
        return ((Number) value).intValue();
    }

    @Test
    void aMissingFileIsAQuietNoOp() {
        ConfigDiagnostics.inspectFile(rangedSpec(), dir.resolve("absent.toml"), "absent.toml");
        // no exception, no file created: inspection observes, it never makes
    }
}
