package org.bitwisemadness.abilitycore.activation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.jetbrains.annotations.Nullable;

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
 */
public final class EligibilityChain {

    private record Registration(EligibilityRule rule) {
    }

    private final List<Registration> rules = new ArrayList<>();
    @Nullable
    private final EligibilityChain parent;

    /** Creates an empty chain. */
    public EligibilityChain() {
        this.parent = null;
    }

    /**
     * Creates a chain that delegates to {@code parent} when all of its own
     * rules pass (composition for consumer-specific rules that must run after
     * the canonical set).
     */
    public EligibilityChain(@Nullable EligibilityChain parent) {
        this.parent = parent;
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
}
