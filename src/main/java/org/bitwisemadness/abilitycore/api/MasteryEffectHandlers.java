package org.bitwisemadness.abilitycore.api;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Named custom effect functions per mastery axis (REQ-CORE-12): a spec
 * points at one by name ({@code withEffect(axis, name)}); without one the
 * axis runs core's default curve. Declared at mod construction like every
 * other named handler; resolution happens at first use with a loud
 * fallback to the default curve (an activation is never failed by an
 * effect read).
 *
 * <p>The returned multiplier is the whole effect: {@code <= 1} for
 * {@link MasteryAxis#COST}/{@link MasteryAxis#CAST_TIME} (a discount),
 * {@code >= 1} for {@link MasteryAxis#POWER}/{@link MasteryAxis#DURATION}
 * (a bonus). Values that are not finite and {@code > 0} are rejected with
 * an error log and the default curve applies instead.</p>
 */
public final class MasteryEffectHandlers {

    /**
     * Computes the effect multiplier one axis applies for one cast.
     * A player may be null in unit fixtures only.
     */
    @FunctionalInterface
    public interface EffectHandler {
        double multiplier(@Nullable ServerPlayer player, ResourceLocation abilityId, MasteryView view);
    }

    private static final Map<MasteryAxis, Map<String, EffectHandler>> HANDLERS = new EnumMap<>(MasteryAxis.class);

    static {
        for (MasteryAxis axis : MasteryAxis.values()) {
            HANDLERS.put(axis, new ConcurrentHashMap<>());
        }
    }

    private MasteryEffectHandlers() {
    }

    /**
     * Declares one named effect handler for one axis.
     *
     * @throws IllegalArgumentException on a null axis/name/handler or blank name
     * @throws IllegalStateException    if the axis already carries the name
     *         (loud ownership conflict, never silent overwrite)
     */
    public static void declare(MasteryAxis axis, String name, EffectHandler handler) {
        if (axis == null) {
            throw new IllegalArgumentException("axis must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("effect handler name must not be blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("effect handler must not be null for " + name);
        }
        EffectHandler previous = HANDLERS.get(axis).putIfAbsent(name, handler);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate mastery effect handler '" + name + "' for " + axis
                            + " - two owners for one name");
        }
    }

    public static Optional<EffectHandler> get(MasteryAxis axis, String name) {
        if (axis == null || name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(HANDLERS.get(axis).get(name));
    }

    public static boolean has(MasteryAxis axis, String name) {
        return axis != null && name != null && HANDLERS.get(axis).containsKey(name);
    }

    /** Test isolation: removes a declared handler. */
    public static boolean remove(MasteryAxis axis, String name) {
        return axis != null && name != null && HANDLERS.get(axis).remove(name) != null;
    }
}
