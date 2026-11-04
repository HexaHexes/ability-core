package org.bitwisemadness.abilitycore.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;
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

    /**
     * Read-only inspection of a config file against its spec (REQ-CFG-03):
     * parses the file into memory, reports every key the spec does not
     * define and every value the spec corrects - both with their key names -
     * and <b>never touches the file on disk</b>: no write, no delete, no
     * rename (the corrections land on the parsed copy only, and NeoForge's
     * own in-memory correction remains the authority). Nothing here may
     * throw - a config load or reload must always continue.
     *
     * <p>Why re-read the file: NeoForge corrects the <em>loaded in-memory
     * model</em> and logs only one generic "Configuration file ... is not
     * correct. Correcting" line - keys are never named, unknown keys are
     * silent, and the operator's file on disk keeps the original values.
     * Re-reading is what makes the diagnostics loud enough to act on.</p>
     */
    public static void inspectFile(ModConfigSpec spec, Path file, String source) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        CommentedConfig raw;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            raw = new TomlParser().parse(reader);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not inspect config file {} ({})", file, e.toString());
            return;
        }
        reportUnknownKeys(spec, raw, source);
        correctAndLog(spec, raw, source); // mutates the in-memory copy only
    }

    /**
     * Logs every key in {@code raw} that the spec does not define, with its
     * key name. NeoForge leaves unknown keys in the file silently - they are
     * ignored here too, never removed (a config file is never rewritten by
     * core; preserving unknown keys is how hand-annotations survive).
     */
    public static void reportUnknownKeys(ModConfigSpec spec, CommentedConfig raw, String source) {
        Set<String> defined = new HashSet<>();
        collectLeafPaths(spec.getValues(), List.of(), defined);
        for (String key : unknownKeys(raw, List.of(), defined)) {
            LOGGER.warn("Config {} contains unknown key {} - ignored, never removed", source, key);
        }
    }

    /**
     * Applies the spec's corrections to {@code raw} - <em>which must be a
     * caller-owned copy, never the live config</em> - logging each replaced
     * value with its key name (the out-of-range/type row of the loud
     * diagnostics). Ground truth: NeoForge <b>clamps</b> out-of-range values
     * into range and <b>resets unreadable values to the default</b> - we
     * observe that rule, never fight it. Missing keys (filled with defaults)
     * and removals are normal load behaviour, not diagnostics, and stay
     * quiet. Returns how many values the correction pass replaced.
     */
    public static int correctAndLog(ModConfigSpec spec, CommentedConfig raw, String source) {
        return spec.correct(raw, (action, path, oldValue, newValue) -> {
            if (oldValue == null || newValue == null) {
                return; // missing key or removal - normal load behaviour
            }
            LOGGER.warn("Config {} key {} holds an invalid value ({}); corrected to {} ({})",
                    source, String.join(".", path), oldValue, newValue, action);
        });
    }

    // --- key walking (the hotbar ConfigLocalisationTest pattern: raw values
    // are UnmodifiableConfig sections or plain Maps; anything else is a leaf).

    private static void collectLeafPaths(UnmodifiableConfig config, List<String> path,
            Set<String> out) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            List<String> here = withKey(path, entry.getKey());
            if (entry.getRawValue() instanceof UnmodifiableConfig section) {
                collectLeafPaths(section, here, out);
            } else {
                out.add(String.join(".", here));
            }
        }
    }

    private static List<String> unknownKeys(Object config, List<String> path, Set<String> defined) {
        List<String> out = new ArrayList<>();
        walkUnknown(config, path, defined, out);
        return out;
    }

    private static void walkUnknown(Object node, List<String> path, Set<String> defined,
            List<String> out) {
        if (node instanceof UnmodifiableConfig config) {
            for (UnmodifiableConfig.Entry entry : config.entrySet()) {
                walkChild(entry.getKey(), entry.getRawValue(), path, defined, out);
            }
        } else if (node instanceof Map<?, ?> table) {
            for (Map.Entry<?, ?> entry : table.entrySet()) {
                walkChild(String.valueOf(entry.getKey()), entry.getValue(), path, defined, out);
            }
        }
    }

    private static void walkChild(String key, Object value, List<String> path,
            Set<String> defined, List<String> out) {
        List<String> here = withKey(path, key);
        if (value instanceof UnmodifiableConfig || value instanceof Map<?, ?>) {
            walkUnknown(value, here, defined, out);
        } else if (!defined.contains(String.join(".", here))) {
            out.add(String.join(".", here));
        }
    }

    private static List<String> withKey(List<String> path, String key) {
        List<String> next = new ArrayList<>(path.size() + 1);
        next.addAll(path);
        next.add(key);
        return next;
    }
}
