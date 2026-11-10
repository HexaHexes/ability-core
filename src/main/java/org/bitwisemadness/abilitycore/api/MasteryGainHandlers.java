package org.bitwisemadness.abilitycore.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Named custom gain functions for mastery (REQ-CORE-12): a spec points at
 * one of these by name ({@code withGainHandler}) instead of its static
 * base gain. Declared at mod construction like every other named handler
 * ({@link ConsumptionStrategies}, {@link AmountResolvers}); resolution
 * happens at first use with a loud fallback.
 */
public final class MasteryGainHandlers {

    /**
     * Computes the XP one use of {@code source} grants under
     * {@code trigger}. A player may be null in unit fixtures only.
     */
    @FunctionalInterface
    public interface GainHandler {
        double gain(@Nullable ServerPlayer player, ResourceLocation source, MasteryTrigger trigger);
    }

    private static final Map<String, GainHandler> HANDLERS = new ConcurrentHashMap<>();

    private MasteryGainHandlers() {
    }

    /**
     * Declares one named gain handler.
     *
     * @throws IllegalArgumentException if the name is null/blank or the handler is null
     * @throws IllegalStateException    if the name is already declared (loud
     *         ownership conflict, never silent overwrite)
     */
    public static void declare(String name, GainHandler handler) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("gain handler name must not be blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("gain handler must not be null for " + name);
        }
        GainHandler previous = HANDLERS.putIfAbsent(name, handler);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate mastery gain handler '" + name + "' - two owners for one name");
        }
    }

    public static Optional<GainHandler> get(String name) {
        return Optional.ofNullable(name == null ? null : HANDLERS.get(name));
    }

    public static boolean has(String name) {
        return name != null && HANDLERS.containsKey(name);
    }

    /** Test isolation: removes a declared handler. */
    public static boolean remove(String name) {
        return name != null && HANDLERS.remove(name) != null;
    }
}
