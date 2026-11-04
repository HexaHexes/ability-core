package org.bitwisemadness.abilitycore.config;

import com.mojang.logging.LogUtils;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Loud-but-safe config diagnostics (REQ-CFG, issue #9 AC3): an invalid value
 * becomes the default, the <b>key and value are logged at WARN</b>, and the
 * optional player-notice hook fires - in that order, never throwing.
 *
 * <p>The helper observes; it never mutates. NeoForge's own load-time
 * correction stays the authority (per SPIKE-cfg-1: "we observe, we don't
 * fight") - this covers the reads that reach core after correction should
 * have happened but didn't: a null, an unreadable backing config, a value
 * the caller's validator rejects.</p>
 *
 * <p><b>Player-notice hook (documented):</b> consumers that surface
 * in-game notices call {@link #setNoticeSink(Consumer)} once at client init
 * with their renderer (chat line, toast, config screen banner - whatever
 * their own surface is). Core never renders; without a sink the diagnostic
 * is log-only. The sink receives an English message; consumers wanting a
 * translatable key map it on their side. Always reset the sink in test
 * teardown.</p>
 */
public final class ConfigDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static volatile Consumer<String> noticeSink;

    private ConfigDiagnostics() {
    }

    /** Installs (or clears, with null) the player-notice hook; see class javadoc. */
    public static void setNoticeSink(@Nullable Consumer<String> sink) {
        noticeSink = sink;
    }

    /**
     * The safe read for one config key: a throwing or null supplier result is
     * reported (key + what came back) and replaced by {@code defaultValue};
     * anything else passes through untouched.
     *
     * @param key          the dotted config key, for the log line
     * @param rawValue     reads the value; must not be cached by callers
     * @param defaultValue the value to run with when the read fails
     * @return the raw value when sane, otherwise the default - never throws
     */
    public static <T> T readOrDefault(String key, Supplier<T> rawValue, T defaultValue) {
        T raw;
        try {
            raw = rawValue.get();
        } catch (RuntimeException e) {
            report(key, "unreadable (" + e + ")", defaultValue);
            return defaultValue;
        }
        if (raw == null) {
            report(key, "null", defaultValue);
            return defaultValue;
        }
        return raw;
    }

    private static void report(String key, String raw, Object defaultValue) {
        LOGGER.warn("Config key {} held an invalid value ({}); using default {}", key, raw, defaultValue);
        Consumer<String> sink = noticeSink;
        if (sink != null) {
            try {
                sink.accept("Config key '" + key + "' was invalid - using its default.");
            } catch (RuntimeException e) {
                // the notice path is diagnostics only: never let it break a read
                LOGGER.warn("Config notice sink failed for {}", key, e);
            }
        }
    }
}
