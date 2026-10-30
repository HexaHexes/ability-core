package org.bitwisemadness.abilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Eligibility chain semantics (REQ-CORE-07): ordering, source scoping
 * (the brief's "combo-only" rule made structural), first-refusal
 * short-circuit, composition, and the rules-refuse-or-pass contract.
 */
class EligibilityChainTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("tensura", "ability");

    private EligibilityChain chain;
    private final List<String> trace = new ArrayList<>();

    @BeforeEach
    void resetTrace() {
        trace.clear();
    }

    @BeforeEach
    void setUp() {
        chain = new EligibilityChain();
        trace.clear();
    }

    private ActivationRequest request(ActivationSource source) {
        return ActivationRequest.of(ID, source);
    }

    private EligibilityRule scoped(ActivationSource scope, String label, boolean refuse) {
        return new EligibilityRule() {
            @Override
            public Optional<ActivationResult> evaluate(ActivationRequest req,
                    net.minecraft.server.level.ServerPlayer player, ActivationContext context) {
                trace.add(label);
                return refuse
                        ? Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED, label))
                        : Optional.empty();
            }

            @Override
            public Set<ActivationSource> appliesTo() {
                return Set.of(scope);
            }
        };
    }

    private EligibilityRule allSources(String label, boolean refuse) {
        return new EligibilityRule() {
            @Override
            public Optional<ActivationResult> evaluate(ActivationRequest req,
                    net.minecraft.server.level.ServerPlayer player, ActivationContext context) {
                trace.add(label);
                return refuse
                        ? Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED, label))
                        : Optional.empty();
            }

            @Override
            public Set<ActivationSource> appliesTo() {
                return Set.of();
            }
        };
    }

    @Test
    void rulesRunInRegistrationOrderAndFirstRefusalWins() {
        chain.register(allSources("rule-1", false));
        chain.register(allSources("rule-2", true));
        chain.register(allSources("rule-3", true));
        var result = chain.check(request(ActivationSource.MENU), null, new ActivationContext(
                request(ActivationSource.MENU), null)).orElseThrow();
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, result.outcome());
        assertEquals("rule-2", result.effectiveMessageKey());
        assertEquals(List.of("rule-1", "rule-2"), trace, "rule-3 must not run after a refusal");
    }

    @Test
    void sourceScopingMakesComboOnlyRulesStructural() {
        // the brief's rule: combo-scoped filters affect COMBO activation only
        chain.register(scoped(ActivationSource.COMBO, "combo-scoped-filter", true));
        chain.register(scoped(ActivationSource.MENU, "menu-rule", true));

        // COMBO: the combo-scoped filter fires and refuses
        var combo = chain.check(request(ActivationSource.COMBO), null, new ActivationContext(
                request(ActivationSource.COMBO), null)).orElseThrow();
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, combo.outcome());
        assertEquals("combo-scoped-filter", combo.effectiveMessageKey());

        // MENU: the combo-scoped filter is skipped silently - the menu rule fires instead
        var menu = chain.check(request(ActivationSource.MENU), null, new ActivationContext(
                request(ActivationSource.MENU), null)).orElseThrow();
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, menu.outcome());
        assertEquals("menu-rule", menu.effectiveMessageKey());

        // DIRECT_SLOT: neither scoped rule applies -> chain passes
        trace.clear();
        var direct = chain.check(request(ActivationSource.DIRECT_SLOT), null, new ActivationContext(
                request(ActivationSource.DIRECT_SLOT), null));
        assertTrue(direct.isEmpty(), "no scoped rule applies - chain passes through as empty");
        assertTrue(trace.isEmpty(), "no rule ran for DIRECT_SLOT");
    }

    @Test
    void emptyAppliesToMeansAllSources() {
        chain.register(allSources("all", true));
        for (ActivationSource source : ActivationSource.values()) {
            var result = chain.check(request(source), null, new ActivationContext(request(source), null)).orElseThrow();
            assertEquals(ActivationOutcome.REFUSED_RESTRICTED, result.outcome());
        }
    }

    @Test
    void parentChainRunsAfterChildRules() {
        var parent = new EligibilityChain();
        parent.register(allSources("parent-rule", true));
        var child = new EligibilityChain(parent);
        child.register(allSources("child-rule", false));

        var result = child.check(request(ActivationSource.MENU), null, new ActivationContext(
                request(ActivationSource.MENU), null)).orElseThrow();
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, result.outcome());
        assertEquals("parent-rule", result.effectiveMessageKey());
    }

    @Test
    void rulesReturningSuccessOutcomesAreContractViolations() {
        chain.register(new EligibilityRule() {
            @Override
            public Optional<ActivationResult> evaluate(ActivationRequest req,
                    net.minecraft.server.level.ServerPlayer player, ActivationContext context) {
                return Optional.of(ActivationResult.ACTED);
            }

            @Override
            public Set<ActivationSource> appliesTo() {
                return Set.of();
            }
        });
        assertThrows(IllegalStateException.class, () -> chain.check(request(ActivationSource.MENU),
                null, new ActivationContext(request(ActivationSource.MENU), null)));
    }

    @Test
    void ruleRegistrationRejectsNull() {
        assertThrows(NullPointerException.class, () -> chain.register(null));
    }
}
