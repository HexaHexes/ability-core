package org.bitwisemadness.abilitycore.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registered mode-change handlers, one per ability id (issue #113, findings
 * #112 recommendation 4): the {@code ActivationHandlerRegistry} shape minus
 * {@link ChargingModel}, because a mode change must never charge or cool
 * down. The flow: consumer keybind -&gt; {@link ModeChangeRequest} -&gt;
 * {@link #run} -&gt; provider handler -&gt; domain half (the provider's
 * native mode packet) -&gt; the {@link ActivationResult} vocabulary; on
 * ACTED the consumer applies its state update, on any refusal nothing moved.
 *
 * <p>Duplicate registration for one id fails loudly (two adapters claiming
 * one ability is the forbidden conflict class), undeclared ids refuse with
 * {@link ActivationOutcome#REFUSED_NO_ADAPTER} rather than guessing, and
 * handlers carry the {@link ModeChangeHandler} contract - no charging, no
 * cooldown, no casts.</p>
 */
public final class ModeChangeHandlers {

    private static final Map<ResourceLocation, ModeChangeHandler> HANDLERS =
            new ConcurrentHashMap<>();

    private ModeChangeHandlers() {
    }

    /**
     * Registers an ability's mode-change handler at mod construction.
     *
     * @throws IllegalArgumentException on null id or null handler
     * @throws IllegalStateException    on duplicate registration (loud conflict)
     */
    public static void register(ResourceLocation abilityId, ModeChangeHandler handler) {
        if (abilityId == null) {
            throw new IllegalArgumentException("ability id must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("mode-change handler must not be null for "
                    + abilityId);
        }
        ModeChangeHandler previous = HANDLERS.putIfAbsent(abilityId, handler);
        if (previous != null) {
            throw new IllegalStateException("Duplicate mode-change declaration for " + abilityId
                    + " - two adapters claim one ability");
        }
    }

    public static Optional<ModeChangeHandler> get(ResourceLocation abilityId) {
        return Optional.ofNullable(abilityId == null ? null : HANDLERS.get(abilityId));
    }

    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && HANDLERS.containsKey(abilityId);
    }

    /**
     * Runs one mode-change intent server-side: no handler answers
     * {@link ActivationOutcome#REFUSED_NO_ADAPTER} (an intent against an
     * unregistered ability never guesses), otherwise the provider's handler
     * answers in the activation vocabulary - consumers act on ACTED only.
     * Handlers must not throw (same contract as activation: an exception
     * escapes and is reported by the caller, never swallowed into a success).
     */
    public static ActivationResult run(ModeChangeRequest request, ServerPlayer player) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        ModeChangeHandler handler = HANDLERS.get(request.abilityId());
        if (handler == null) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_NO_ADAPTER);
        }
        return handler.change(request, player);
    }

    /** Test isolation / future reload paths. */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && HANDLERS.remove(abilityId) != null;
    }
}
