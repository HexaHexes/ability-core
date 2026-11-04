package org.bitwisemadness.abilitycore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The loud-but-safe diagnostics contract (issue #9 AC3): an invalid value
 * becomes the default, the key and what came back are logged at WARN, the
 * player-notice hook fires - and nothing ever throws. The helper observes;
 * NeoForge's load-time correction stays the authority.
 */
class ConfigDiagnosticsTest {

    private final List<String> notices = new ArrayList<>();

    @AfterEach
    void clearSink() {
        ConfigDiagnostics.setNoticeSink(null);
        notices.clear();
    }

    @Test
    void unreadableValueFallsBackLoudly() {
        ConfigDiagnostics.setNoticeSink(notices::add);
        try (LogCapture logs = LogCapture.forLogger(ConfigDiagnostics.class, Level.WARN)) {
            int value = ConfigDiagnostics.readOrDefault("diagnostics.refusalLog", () -> {
                throw new IllegalStateException("corrupt backing config");
            }, 7);

            assertEquals(7, value, "an unreadable value must fall back to the default");
            assertEquals(1, logs.messages().size(), "the fallback must be logged exactly once");
            String line = logs.messages().get(0);
            assertTrue(line.contains("diagnostics.refusalLog"), line);
            assertTrue(line.contains("corrupt backing config"), line);
            assertEquals(1, notices.size(), "the notice hook must fire once");
            assertTrue(notices.get(0).contains("diagnostics.refusalLog"), notices.get(0));
        }
    }

    @Test
    void nullValueFallsBackToDefault() {
        ConfigDiagnostics.setNoticeSink(notices::add);
        try (LogCapture logs = LogCapture.forLogger(ConfigDiagnostics.class, Level.WARN)) {
            String value = ConfigDiagnostics.readOrDefault("diagnostics.playerNotices",
                    () -> null, "defaulted");
            assertEquals("defaulted", value);
            assertEquals(1, logs.messages().size());
            assertTrue(logs.messages().get(0).contains("null"), logs.messages().get(0));
            assertEquals(1, notices.size());
        }
    }

    @Test
    void saneValuePassesThroughUnlogged() {
        ConfigDiagnostics.setNoticeSink(notices::add);
        try (LogCapture logs = LogCapture.forLogger(ConfigDiagnostics.class, Level.WARN)) {
            String value = ConfigDiagnostics.readOrDefault("diagnostics.refusalLog",
                    () -> "every", "once-per-key");
            assertEquals("every", value, "a sane value must pass through untouched");
            assertTrue(logs.messages().isEmpty(), "a sane read must not log: " + logs.messages());
            assertTrue(notices.isEmpty(), "a sane read must not notify");
        }
    }

    @Test
    void aThrowingNoticeSinkNeverBreaksTheRead() {
        ConfigDiagnostics.setNoticeSink(notice -> {
            throw new IllegalStateException("renderer is down");
        });
        try (LogCapture logs = LogCapture.forLogger(ConfigDiagnostics.class, Level.WARN)) {
            String value = ConfigDiagnostics.readOrDefault("diagnostics.refusalLog",
                    () -> null, "defaulted");
            assertEquals("defaulted", value, "a broken notice sink must not fail the read");
            assertTrue(logs.messages().size() >= 1, "the diagnostics themselves must still log");
        }
    }
}
