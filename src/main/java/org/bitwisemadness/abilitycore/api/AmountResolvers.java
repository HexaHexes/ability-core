package org.bitwisemadness.abilitycore.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Registered amount resolvers, one per name (REQ-CORE-04 part 4,
 * issue #103): an ability's {@code amount_resolver} field names the function
 * that prices its declared {@code costs} <b>for a specific activation</b>.
 *
 * <p>A resolver returns the declared list priced for this request: same
 * resource ids, same order, per-request amounts. Selection (issue #90) decides
 * <em>which</em> entries pay; resolution decides <em>how much</em> they cost -
 * the pipeline resolves <b>before</b> the strategy runs, so built-in consults
 * ({@code random}, {@code in_order}) and custom selectors all observe priced
 * amounts.</p>
 *
 * <p>{@code resolvedTarget} is the server-stashed
 * {@code TargetValidationStep.RESOLVED_ENTITY} value for {@code EntityHint}
 * targets (already resolved at ELIGIBILITY, before the RESOURCE phase) and
 * null otherwise - opaque by doctrine: the resolver owns the cast, exactly as
 * handlers and providers do. A resolver that needs the player for
 * {@code Self} has {@code player}.</p>
 *
 * <p><b>Trust and validation:</b> resolvers are construction-time registered
 * code (the same trust as handlers and strategies) and datapacks may only
 * <em>name</em> an already-declared resolver. Declared amounts are the nominal
 * estimate - there is no clamping to them; the pipeline re-validates every
 * returned entry (size, id order, {@code >= 0}, finite) and refuses fail-closed
 * on any violation. Timing contract matches providers/consumers/strategies:
 * declare at construction; definitions register at {@code RegisterEvent}, so
 * registration-time name resolution is order-safe.</p>
 */
public final class AmountResolvers {

    /** The built-in identity: declared amounts pass through unchanged. */
    public static final String STATIC = "static";

    /**
     * Prices one ability's declared costs for one activation.
     *
     * @param declared       the definition's costs (immutable, in list order)
     * @param request        the activation (pricing may read slot/target/source)
     * @param player         the activating player, or null in unit fixtures
     * @param resolvedTarget the server-stashed resolved target for
     *                       {@code EntityHint} hints, else null - opaque
     * @return the priced list: same ids, same order, per-request amounts
     */
    @FunctionalInterface
    public interface AmountResolver {
        List<ResourceCost> resolve(List<ResourceCost> declared, ActivationRequest request,
                @Nullable ServerPlayer player, @Nullable Object resolvedTarget);
    }

    private static final Map<String, AmountResolver> RESOLVERS = new ConcurrentHashMap<>();

    static {
        // core owns the name: the identity resolver is pre-registered like the
        // strategy built-ins, so a datapack can always name "static"
        declare(STATIC, (declared, request, player, resolvedTarget) -> declared);
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
            throw new IllegalArgumentException("resolver name must not be blank");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null for " + name);
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

    /** Test isolation: removes a declared resolver (the built-in included). */
    public static boolean remove(String name) {
        return name != null && RESOLVERS.remove(name) != null;
    }
}
