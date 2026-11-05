package org.bitwisemadness.abilitycore.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Registered consumption strategies, one per name (REQ-CORE-04 part 3,
 * issue #90): an ability's {@code consumption_strategy} field names which
 * function decides <b>how</b> its {@code costs} entries are consumed.
 *
 * <p><b>Selection vs validation.</b> A strategy only <em>selects</em> - it
 * returns the ordered subset of entries to charge (or an empty list, which
 * the pipeline refuses as unaffordable: nothing charged). Core then validates
 * <b>exactly the selected entries</b> fail-closed and charges them exactly
 * once, in returned order. Validation scope belongs to the strategy: a
 * selector that intentionally skips an entry must not be refused for it.</p>
 *
 * <p><b>Built-ins</b> (pre-registered):</p>
 * <ul>
 *   <li>{@link #ALL} - select every entry (validate all, charge in list
 *       order); the default, so existing JSON is unaffected.</li>
 *   <li>{@link #RANDOM} - re-roll among affordable: consult
 *       {@link AffordabilityCheck} for every entry, uniform pick among the
 *       SUFFICIENT ones; none affordable -&gt; empty selection -&gt;
 *       refusal.</li>
 *   <li>{@link #IN_ORDER} - recorded verbatim: "Consume from each in order,
 *       till the cost is satisfied; if cost can not be satisfied, fail before
 *       consuming". Operational reading (confirm on review): consult entries
 *       in list order, <b>early-abort at the first that cannot be fully
 *       covered</b> (empty selection -&gt; refusal, later entries never
 *       consulted, nothing charged); full pass -&gt; select all in list
 *       order.</li>
 * </ul>
 *
 * <p><b>Custom strategies are registerable code</b>: {@link #declare} at mod
 * construction (same timing contract as providers/consumers - definitions
 * register at {@code RegisterEvent}, after all mods are constructed), so a
 * datapack can name a strategy an addon registered. An unknown name fails
 * loudly at definition registration (see
 * {@code AbilityDefinition.validateOrThrow}). The request is passed so
 * selectors can inspect activation context - the planning example:
 * pseudocore reads the item at the request's slot context, checks its
 * enchantments, and returns only the aura entry (or only magicule, or both).
 *
 * <p>Strategies are pure selection functions: they must not mutate state,
 * never charge, and their {@link AffordabilityCheck} is a read-only consult
 * (NATIVE/EXTERNAL agnostic - charging stays at core's single EXTERNAL site).</p>
 */
public final class ConsumptionStrategies {

    /** Select every entry - the default (existing JSON resolves here). */
    public static final String ALL = "all";
    /** Re-roll among affordable entries; empty selection when none is affordable. */
    public static final String RANDOM = "random";
    /** Consume in list order with early abort at the first uncovered entry. */
    public static final String IN_ORDER = "in_order";

    /**
     * Read-only affordability consult for one entry during selection.
     * Never charges; a missing provider or a contract-violating null verdict
     * reads as {@link Affordability#unknown} (not affordable, fail closed).
     */
    @FunctionalInterface
    public interface AffordabilityCheck {
        Affordability check(ResourceCost entry);
    }

    /**
     * Decides which entries an activation consumes.
     *
     * @param request the activation (selectors may inspect slot/target/source)
     * @param player  the activating player, or null in unit fixtures
     * @param entries the definition's declared costs (immutable, in list order)
     * @param check   read-only affordability consult
     * @return the ordered subset to charge; empty = refuse (nothing charged)
     */
    @FunctionalInterface
    public interface Strategy {
        List<ResourceCost> select(ActivationRequest request, @Nullable ServerPlayer player,
                List<ResourceCost> entries, AffordabilityCheck check);
    }

    private static final Map<String, Strategy> STRATEGIES = new ConcurrentHashMap<>();

    /** RNG seam for {@link #RANDOM} (package-private, tests): deterministic picks. */
    private static Random random = new Random();

    static {
        declare(ALL, (request, player, entries, check) -> entries);

        declare(RANDOM, (request, player, entries, check) -> {
            List<ResourceCost> affordable = new ArrayList<>(entries.size());
            for (ResourceCost entry : entries) {
                Affordability verdict = check.check(entry);
                if (verdict != null && verdict.state() == Affordability.State.SUFFICIENT) {
                    affordable.add(entry);
                }
            }
            if (affordable.isEmpty()) {
                return List.of(); // nothing to re-roll among -> refusal
            }
            return List.of(affordable.get(random.nextInt(affordable.size())));
        });

        declare(IN_ORDER, (request, player, entries, check) -> {
            for (ResourceCost entry : entries) {
                Affordability verdict = check.check(entry);
                if (verdict == null || verdict.state() != Affordability.State.SUFFICIENT) {
                    // "if cost can not be satisfied, fail before consuming" -
                    // empty selection refuses everything, later entries untouched
                    return List.of();
                }
            }
            return entries; // satisfied in order -> consume all, in list order
        });
    }

    private ConsumptionStrategies() {
    }

    /**
     * Declares one named strategy.
     *
     * @throws IllegalArgumentException if the name is null/blank or the strategy is null
     * @throws IllegalStateException    if the name is already declared (loud
     *         ownership conflict, never silent overwrite)
     */
    public static void declare(String name, Strategy strategy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("strategy name must not be blank");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null for " + name);
        }
        Strategy previous = STRATEGIES.putIfAbsent(name, strategy);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate consumption strategy '" + name + "' - two owners for one name");
        }
    }

    public static Optional<Strategy> get(String name) {
        return Optional.ofNullable(name == null ? null : STRATEGIES.get(name));
    }

    /** Existence query for registration-time validation (issue #90). */
    public static boolean has(String name) {
        return name != null && STRATEGIES.containsKey(name);
    }

    /** Test isolation: removes a declared strategy (built-ins included). */
    public static boolean remove(String name) {
        return name != null && STRATEGIES.remove(name) != null;
    }

    /**
     * Presence-resolved RNG seam (package-private, tests): fixtures inject a
     * scripted Random for deterministic {@link #RANDOM} picks; null restores
     * the default.
     */
    static void setRandomForTests(@Nullable Random replacement) {
        random = replacement == null ? new Random() : replacement;
    }
}
