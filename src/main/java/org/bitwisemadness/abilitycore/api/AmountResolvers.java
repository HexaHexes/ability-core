package org.bitwisemadness.abilitycore.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Registered cost amount resolvers, one per name (REQ-CORE-04 p4, issue
 * #103): an ability's {@code amount_resolver} field names which function
 * prices its declared {@code costs} entries for one activation.
 *
 * <p><b>Pricing vs selection.</b> A strategy selects <em>which</em>
 * entries pay; a resolver decides <em>how much</em> each declared entry
 * costs for this request. Resolution runs before selection, so the
 * built-in consults ({@code random}, {@code in_order}) and custom
 * strategies always observe priced amounts, and the pipeline validates,
 * snapshots and charges the priced entries through the unchanged
 * amount-parameterized path ({@link ResourceProvider#check} receives the
 * priced amount, the consumer receives that snapshot verbatim).</p>
 *
 * <p><b>Built-ins</b> (pre-registered): {@link #STATIC} - the identity,
 * declared amounts pass through unchanged, so existing definitions and
 * datapacks behave exactly as before issue #103.</p>
 *
 * <p>Custom resolvers are registerable code: {@link #declare} at mod
 * construction (same timing contract as providers/consumers/strategies -
 * definitions register at {@code RegisterEvent}, after all mods are
 * constructed), so a datapack can name a resolver an addon registered.
 * An unknown name fails loudly at definition registration (see
 * {@code AbilityDefinition.validateOrThrow}). The resolved target is the
 * server-stashed value for entity-hint targets
 * ({@code TargetValidationStep.RESOLVED_ENTITY}), passed opaquely - the
 * resolver owns the cast, exactly as handlers and providers do; a
 * resolver that needs the player (e.g. {@code Self}) has {@code player}.</p>
 *
 * <p>Resolvers are pure pricing functions: they must not mutate state and
 * never charge. The pipeline fail-closes on every contract violation (the
 * failure table in {@code ResourceChargeStep}): null result, size or
 * order or resource-id mismatch against the declared list, a non-finite
 * or negative amount, a vanished name, or a throwing resolver - each
 * refuses the activation with nothing charged.</p>
 */
public final class AmountResolvers {

    /** Identity - declared amounts pass through unchanged (the default). */
    public static final String STATIC = "static";

    /**
     * Prices the declared cost list for one activation.
     *
     * @param declared       the definition's declared costs (immutable, in
     *                       list order)
     * @param request        the activation (resolvers may inspect
     *                       slot/target/source context)
     * @param player         the activating player, or null in unit fixtures
     * @param resolvedTarget the server-resolved value for an entity-hint
     *                       target, or null when the request carries none;
     *                       opaque, the resolver owns the cast
     * @return a same-size list mirroring the declared resource ids in
     *         order, carrying this request's amounts; never null
     */
    @FunctionalInterface
    public interface AmountResolver {
        List<ResourceCost> resolve(List<ResourceCost> declared, ActivationRequest request,
                @Nullable ServerPlayer player, @Nullable Object resolvedTarget);
    }

    private static final Map<String, AmountResolver> RESOLVERS = new ConcurrentHashMap<>();

    static {
        declare(STATIC, (declared, request, player, target) -> declared);
    }

    private AmountResolvers() {
    }

    /**
     * Declares one named resolver.
     *
     * @throws IllegalArgumentException if the name is null/blank or the resolver is null
     * @throws IllegalStateException    if the name is already declared (loud
     *         ownership conflict, never silent overwrite)
     */
    public static void declare(String name, AmountResolver resolver) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("amount resolver name must not be blank");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("amount resolver must not be null for " + name);
        }
        AmountResolver previous = RESOLVERS.putIfAbsent(name, resolver);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate amount resolver '" + name + "' - two owners for one name");
        }
    }

    public static Optional<AmountResolver> get(String name) {
        return Optional.ofNullable(name == null ? null : RESOLVERS.get(name));
    }

    /** Existence query for registration-time validation (issue #103). */
    public static boolean has(String name) {
        return name != null && RESOLVERS.containsKey(name);
    }

    /** Test isolation: removes a declared resolver (built-ins included). */
    public static boolean remove(String name) {
        return name != null && RESOLVERS.remove(name) != null;
    }
}
