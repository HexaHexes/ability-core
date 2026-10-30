package org.bitwisemadness.abilitycore.activation;

import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;

/**
 * One policy rule in the eligibility chain (REQ-CORE-07).
 *
 * <p>Rules are the extension point for policy providers: casting policies
 * (policy providers), progression gates
 * (bw#18) - anything that must refuse a request before resources are
 * validated or the provider runs.</p>
 *
 * <p><b>Source scoping is part of the contract:</b> a rule declares the
 * activation sources it applies to; an empty set means ALL sources. This is
 * what makes the brief's "filters affect combo activation only" rule
 * structural - a combo-chain filter declares {@code Set.of(ActivationSource.COMBO)}
 * and the chain never invokes it for menu or native-adapter requests.</p>
 *
 * <p>Return contract: {@code Optional.empty()} = this rule permits (chain
 * continues); present = a terminal refusal that short-circuits the whole
 * pipeline. Rules are read-only predicates over server state - they must not
 * consume, mutate, or activate anything.</p>
 */
public interface EligibilityRule {

    /**
     * Evaluates the rule for one request.
     *
     * @return empty = permitted by this rule; present = terminal refusal
     */
    Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player, ActivationContext context);

    /**
     * The activation sources this rule applies to. Empty = all sources.
     * Fail-closed convention for *content*: an unknown/invalid source on the
     * wire never reaches rules at all (the request layer refuses first).
     */
    Set<ActivationSource> appliesTo();


}
