package org.bitwisemadness.abilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The ordered eligibility chain (REQ-CORE-07), run as the pipeline's first
 * ELIGIBILITY-phase step - after target resolution (so rules see resolved
 * targets via the context) and before cooldown/resource validation.
 *
 * <p>Ordering: rules run in registration order; the first present result
 * short-circuits the entire pipeline (ELIGIBILITY refusals precede COOLDOWN,
 * RESOURCE, ACTIVATION and CHARGE - order-traced in the tests).</p>
 *
 * <p>Source scoping: a rule whose {@code appliesTo()} excludes the request's
 * source is skipped silently - it did not run, which is different from
 * "it ran and permitted" (observable in the trace for debugging, but with
 * identical pipeline semantics).</p>
 *
 * <p>Fail-closed defaults: an unregistered ability id is refused by the
 * pipeline's RESOLVE phase before the chain runs; the chain itself never
 * grants anything - it can only refuse or pass through to the remaining
 * phases.</p>
 *
 * <p>Instances are the extension point: policy providers construct their own
 * chain (or register rules on the canonical one from
 * {@code AbilityCore}) at their mod init. Duplicate rule objects are
 * permitted (the same rule may be registered on several chains); there is no
 * global static state.</p>
 *
 * <p>Diagnostics (REQ-CORE-07 acceptance, tunable per issue #9): a refusal
 * debug-logs its reason - ability, source, outcome and message key - under
 * the server's {@code diagnostics.refusalLog} mode: {@code once-per-key}
 * (default; one line per key per chain instance, so holding the activation
 * key cannot spam - repeats are suppressed until the bounded set
 * ({@value #MAX_LOGGED_KEYS} keys, cleared on overflow) wraps), {@code every}
 * (each press logs - for debugging a stuck activation), or {@code off}. The
 * mode is read <b>live on every refusal</b> via {@link CoreConfig#refusalLogMode}
 * - never cached - so a config reload applies to the next refusal without a
 * restart. Permitted requests and rules scoped out of the source are never
 * logged - they are not refusals.</p>
 */
public final class EligibilityChain {

    /** Once-per-key cap: the refusal set clears when it reaches this size. */
    private static final int MAX_LOGGED_KEYS = 1024;

    private static final Logger LOGGER = LogUtils.getLogger();

    private record Registration(EligibilityRule rule) {
    }

    private final List<Registration> rules = new ArrayList<>();
    private final Set<String> loggedRefusals = ConcurrentHashMap.newKeySet();
    @Nullable
    private final EligibilityChain parent;
    private final Supplier<CoreConfig.RefusalLogMode> refusalLog;

    /** Creates an empty chain. */
    public EligibilityChain() {
        this(null, CoreConfig::refusalLogMode);
    }

    /**
     * Creates a chain that delegates to {@code parent} when all of its own
     * rules pass (composition for consumer-specific rules that must run after
     * the canonical set).
     */
    public EligibilityChain(@Nullable EligibilityChain parent) {
        this(parent, CoreConfig::refusalLogMode);
    }

    /** Test seam: injects the refusal-log mode supplier (live read contract). */
    EligibilityChain(@Nullable EligibilityChain parent, Supplier<CoreConfig.RefusalLogMode> refusalLog) {
        this.parent = parent;
        this.refusalLog = Objects.requireNonNull(refusalLog, "refusalLog");
    }

    /** Registers a rule at the end of the chain (registration order = evaluation order). */
    public void register(EligibilityRule rule) {
        rules.add(new Registration(Objects.requireNonNull(rule, "rule")));
    }

    /** Number of directly registered rules (excludes the parent chain). */
    public int size() {
        return rules.size();
    }

    /**
     * Runs the chain: first present refusal short-circuits; empty = every rule
     * permitted (and the parent chain, if composed, also permitted).
     */
    public Optional<ActivationResult> check(ActivationRequest request, ServerPlayer player,
            ActivationContext context) {
        for (Registration registration : rules) {
            EligibilityRule rule = registration.rule();
            if (!rule.appliesTo().isEmpty() && !rule.appliesTo().contains(request.source())) {
                continue; // scoped out: silently not applicable
            }
            Optional<ActivationResult> verdict = rule.evaluate(request, player, context);
            if (verdict.isPresent()) {
                ActivationResult result = verdict.get();
                if (result.outcome().isSuccess()) {
                    // a rule claiming success is a contract violation: rules refuse or pass
                    throw new IllegalStateException(
                            "Eligibility rule returned a success outcome - rules may only refuse or pass");
                }
                logRefusal(request, result);
                return Optional.of(result);
            }
        }
        if (parent != null) {
            return parent.check(request, player, context);
        }
        return Optional.empty();
    }

    /** Adapter exposing this chain as a single ELIGIBILITY-phase pipeline step. */
    public ActivationPipeline.Step asStep() {
        return (request, player, context) -> check(request, player, context);
    }

    /**
     * Debug-logs a refusal under the live {@code diagnostics.refusalLog}
     * mode: {@code off} silences, {@code every} logs each press, and the
     * default {@code once-per-key} suppresses repeats (keyed on ability,
     * source, outcome and message) until the bounded set clears on overflow.
     * The mode is read per refusal - never cached - so a reload applies
     * immediately.
     */
    private void logRefusal(ActivationRequest request, ActivationResult result) {
        CoreConfig.RefusalLogMode mode = refusalLog.get();
        if (mode == CoreConfig.RefusalLogMode.OFF) {
            return;
        }
        String key = request.abilityId() + "|" + request.source() + "|"
                + result.outcome() + "|" + result.effectiveMessageKey();
        if (mode == CoreConfig.RefusalLogMode.EVERY || addLoggedKey(key)) {
            LOGGER.debug("Refusing {} ({} from {}): {}", request.abilityId(),
                    result.outcome(), request.source(), result.effectiveMessageKey());
        }
    }

    /** Bounded once-per-key set: clears wholesale when full, then records. */
    private boolean addLoggedKey(String key) {
        if (loggedRefusals.size() >= MAX_LOGGED_KEYS) {
            loggedRefusals.clear();
        }
        return loggedRefusals.add(key);
    }
}
