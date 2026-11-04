package org.bitwisemadness.abilitycore;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/**
 * Captures the messages a logger emits while the capture is open, so fixtures
 * can assert on diagnostics (e.g. REQ-CORE-07's "refusal reasons debug-logged
 * without spam").
 *
 * <p>Attaches an in-memory appender to the named logger and forces its level
 * to the requested level for the duration, so assertions behave the same under
 * any ambient log configuration (the test JVM ships no log4j2 config of its
 * own). {@link #close()} detaches the appender and restores the previous
 * logger state - always use try-with-resources.</p>
 */
public final class LogCapture implements AutoCloseable {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final LoggerContext context;
    private final String loggerName;
    private final LoggerConfig loggerConfig;
    private final boolean preexistingLoggerConfig;
    private final Level previousLevel;
    private final CapturingAppender appender;

    private LogCapture(LoggerContext context, String loggerName, LoggerConfig loggerConfig,
            boolean preexistingLoggerConfig, Level previousLevel, CapturingAppender appender) {
        this.context = context;
        this.loggerName = loggerName;
        this.loggerConfig = loggerConfig;
        this.preexistingLoggerConfig = preexistingLoggerConfig;
        this.previousLevel = previousLevel;
        this.appender = appender;
    }

    /**
     * Opens a capture on the logger of the given class (its {@code LogUtils}
     * name) at the given level - DEBUG for debug-logged diagnostics.
     */
    public static LogCapture forLogger(Class<?> loggerOwner, Level level) {
        String loggerName = loggerOwner.getName();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig before = config.getLoggerConfig(loggerName);
        boolean preexisting = loggerName.equals(before.getName());
        Level previousLevel = before.getLevel();

        // Force the level so assertions do not depend on the ambient config;
        // this creates an exact LoggerConfig for the name when none existed.
        Configurator.setLevel(loggerName, level);
        LoggerConfig target = config.getLoggerConfig(loggerName);

        CapturingAppender appender =
                new CapturingAppender("log-capture-" + SEQUENCE.incrementAndGet());
        appender.start();
        target.addAppender(appender, Level.ALL, null);

        return new LogCapture(context, loggerName, target, preexisting, previousLevel, appender);
    }

    /** Every message logged through the captured logger while open, formatted. */
    public List<String> messages() {
        return List.copyOf(appender.messages);
    }

    @Override
    public void close() {
        loggerConfig.removeAppender(appender.getName());
        appender.stop();
        if (preexistingLoggerConfig) {
            if (previousLevel != null) {
                Configurator.setLevel(loggerName, previousLevel);
            }
        } else {
            // We created this LoggerConfig via setLevel: drop it so the logger
            // falls back to whatever it resolved to before the capture.
            context.getConfiguration().removeLogger(loggerName);
        }
    }

    /** Collects formatted messages; copy-on-write so late events cannot race close. */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        private CapturingAppender(String name) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
