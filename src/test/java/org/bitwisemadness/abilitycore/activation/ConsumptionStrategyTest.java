package org.bitwisemadness.abilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pipeline integration for consumption strategies (issue #90): the built-in
 * matrix (each built-in x {all sufficient, one insufficient, unknown, null}),
 * selection-scoped validation, exactly-once charging in the strategy's
 * returned order, and the contract guards. Events are tagged strings
 * ({@code check:<id>}/{@code charge:<id>}/{@code activate}) so every fixture
 * pins the exact sequence.
 */
class ConsumptionStrategyTest {

    private static final ResourceLocation ALL_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_all");
    private static final ResourceLocation RANDOM_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_random");
    private static final ResourceLocation ORDER_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_in_order");
    private static final ResourceLocation SOLO_ORDER_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_solo_in_order");
    private static final ResourceLocation CUSTOM_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_custom");
    private static final ResourceLocation SCOPE_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_scope");
    private static final ResourceLocation FABRICATE_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_fabricate");
    private static final ResourceLocation NULL_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_null_return");
    private static final ResourceLocation VANISHING_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "strat_vanishing");
    private static final ResourceLocation SKILL_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "skill");

    private static final String ONE = "test:one";
    private static final String TWO = "test:two";
    private static final ResourceCost COST_ONE = new ResourceCost(ONE, 10.0);
    private static final ResourceCost COST_TWO = new ResourceCost(TWO, 5.0);

    /** Stub for "the item in the request's slot" (issue #90's selector example). */
    private static final Map<Integer, String> SLOT_ENCHANTS = Map.of(
            0, "aura",
            1, "magicule",
            2, "none");

    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);

        register(fixture, ALL_ABILITY, ConsumptionStrategies.ALL);
        register(fixture, RANDOM_ABILITY, ConsumptionStrategies.RANDOM);
        register(fixture, ORDER_ABILITY, ConsumptionStrategies.IN_ORDER);
        Registry.register(fixture, SOLO_ORDER_ABILITY, new AbilityDefinition(
                "tensura.skill.strat", null, SKILL_CATEGORY,
                ActivationType.INSTANT, List.of(COST_ONE), ConsumptionStrategies.IN_ORDER,
                AmountResolvers.STATIC, Set.of(), Map.of()));
        register(fixture, CUSTOM_ABILITY, "test:slot_selector");
        register(fixture, SCOPE_ABILITY, "test:only_second");
        register(fixture, FABRICATE_ABILITY, "test:fabricate");
        register(fixture, NULL_ABILITY, "test:null_return");
        register(fixture, VANISHING_ABILITY, "test:vanishing");
        AbilityRegistries.installRegistryForTests(fixture);

        // custom strategies, declared like an addon at construction
        ConsumptionStrategies.declare("test:only_second",
                (request, player, entries, check) -> List.of(entries.get(1)));
        ConsumptionStrategies.declare("test:slot_selector", (request, player, entries, check) -> {
            String enchant = SLOT_ENCHANTS.get(request.slotContext());
            if ("aura".equals(enchant)) {
                return List.of(entries.get(0));
            }
            if ("magicule".equals(enchant)) {
                return List.of(entries.get(1));
            }
            return entries; // no relevant enchant: both halves (pseudocore#174's default)
        });
        ConsumptionStrategies.declare("test:fabricate",
                (request, player, entries, check) -> List.of(new ResourceCost("test:undeclared", 1.0)));
        ConsumptionStrategies.declare("test:null_return", (request, player, entries, check) -> null);
        ConsumptionStrategies.declare("test:vanishing", (request, player, entries, check) -> entries);

        handlers.register(ALL_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(RANDOM_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(ORDER_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(SOLO_ORDER_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(CUSTOM_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(SCOPE_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(FABRICATE_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(NULL_ABILITY, acting(), ChargingModel.EXTERNAL);
        handlers.register(VANISHING_ABILITY, acting(), ChargingModel.EXTERNAL);
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        for (String name : new String[] {"test:only_second", "test:slot_selector",
                "test:fabricate", "test:null_return", "test:vanishing"}) {
            ConsumptionStrategies.remove(name);
        }
        for (String id : new String[] {ONE, TWO}) {
            ResourceConsumers.remove(id);
            ResourceProviders.remove(id);
        }
    }

    private void register(Registry<AbilityDefinition> fixture, ResourceLocation id, String strategy) {
        Registry.register(fixture, id, new AbilityDefinition(
                "tensura.skill.strat", null, SKILL_CATEGORY,
                ActivationType.INSTANT, List.of(COST_ONE, COST_TWO), strategy,
                AmountResolvers.STATIC, Set.of(), Map.of()));
    }

    private ActivationHandler acting() {
        return (req, player) -> {
            events.add("activate");
            return ActivationResult.ACTED;
        };
    }

    /**
     * Declares both halves for {@code id}: check and charge record tagged
     * events; a null verdict stands in for a contract-violating provider.
     */
    private void declareVerdict(String id, Affordability verdict) {
        ResourceProviders.declare(id, (abilityId, player, amount) -> {
            events.add("check:" + id);
            return verdict;
        });
        ResourceConsumers.declare(id, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                events.add("charge:" + snapshot.resourceId());
            }
        });
    }

    private void swapVerdict(String id, Affordability verdict) {
        ResourceProviders.remove(id);
        ResourceConsumers.remove(id);
        declareVerdict(id, verdict);
    }

    private ActivationResult run(ResourceLocation ability) {
        return pipeline.run(ActivationRequest.of(ability, ActivationSource.DIRECT_SLOT), null);
    }

    private static Affordability sufficient(String id) {
        return Affordability.sufficient(id, 1.0);
    }

    @Test
    void allStrategyMatrix() {
        // all sufficient -> validated in order, charged in order, exactly once
        declareVerdict(ONE, sufficient(ONE));
        declareVerdict(TWO, sufficient(TWO));
        assertEquals(ActivationOutcome.ACTED, run(ALL_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO, "activate",
                "charge:" + ONE, "charge:" + TWO), events);

        // one insufficient -> refusal, nothing charged
        events.clear();
        swapVerdict(TWO, Affordability.insufficient(TWO, 5.0));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(ALL_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO), events,
                "validated up to the failure: no activate, no charge");

        // UNKNOWN -> fail closed, same shape
        events.clear();
        swapVerdict(TWO, Affordability.unknown(TWO));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(ALL_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO), events);

        // contract-violating null verdict -> no_provider refusal
        events.clear();
        swapVerdict(TWO, null);
        var refused = run(ALL_ABILITY);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, refused.outcome());
        assertEquals("ability.refused.unaffordable.no_provider", refused.effectiveMessageKey());
        assertEquals(List.of("check:" + ONE, "check:" + TWO), events);
    }

    @Test
    void randomStrategyMatrix() {
        // both sufficient -> exactly one charge, chosen among the affordable
        declareVerdict(ONE, sufficient(ONE));
        declareVerdict(TWO, sufficient(TWO));
        assertEquals(ActivationOutcome.ACTED, run(RANDOM_ABILITY).outcome());
        assertEquals(5, events.size(), "2 consults + 1 validation + activate + charge");
        assertEquals("check:" + ONE, events.get(0));
        assertEquals("check:" + TWO, events.get(1));
        String picked = events.get(2).substring("check:".length());
        assertTrue(Set.of(ONE, TWO).contains(picked), "picked from the affordable set: " + picked);
        assertEquals("activate", events.get(3));
        assertEquals("charge:" + picked, events.get(4), "charged exactly what it validated");

        // one insufficient -> deterministic: only the affordable one can win
        events.clear();
        swapVerdict(TWO, Affordability.insufficient(TWO, 5.0));
        assertEquals(ActivationOutcome.ACTED, run(RANDOM_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO, "check:" + ONE,
                "activate", "charge:" + ONE), events);

        // none affordable -> empty selection -> refusal, nothing charged
        events.clear();
        swapVerdict(ONE, Affordability.insufficient(ONE, 10.0));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(RANDOM_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO), events,
                "both consulted, none selected: no activate, no charge");

        // UNKNOWN and a null verdict are both 'not affordable' at selection
        events.clear();
        swapVerdict(ONE, Affordability.unknown(ONE));
        swapVerdict(TWO, null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(RANDOM_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO), events,
                "selection absorbs the null verdict as unknown - refusal, nothing charged");
    }

    @Test
    void inOrderStrategyMatrix() {
        // full pass -> consulted in order, validated in order, charged in order
        declareVerdict(ONE, sufficient(ONE));
        declareVerdict(TWO, sufficient(TWO));
        assertEquals(ActivationOutcome.ACTED, run(ORDER_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO, "check:" + ONE, "check:" + TWO,
                "activate", "charge:" + ONE, "charge:" + TWO), events,
                "selection consults, then core validates the selected subset, then charges");

        // abort at the second entry: fail before consuming, nothing charged
        events.clear();
        swapVerdict(TWO, Affordability.insufficient(TWO, 5.0));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(ORDER_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO), events,
                "abort at the failure point: validation never ran, nothing stashed");

        // abort at the first entry: the second is never even consulted
        events.clear();
        swapVerdict(ONE, Affordability.insufficient(ONE, 10.0));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(ORDER_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE), events, "early abort: TWO untouched");

        // a null verdict aborts the same way (wrapper reads it as unknown)
        events.clear();
        swapVerdict(ONE, sufficient(ONE));
        swapVerdict(TWO, null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(ORDER_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO), events);
    }

    @Test
    void inOrderCoversTheFullCostWithOneResourceWhenTheDeclarationSaysSo() {
        // owner confirmation (2026-11-05): "either the full cost can be covered
        // by one of the resources" - a one-entry declaration pays with that one
        // resource; its brokered counterpart is the matrix full-pass case
        declareVerdict(ONE, sufficient(ONE));
        assertEquals(ActivationOutcome.ACTED, run(SOLO_ORDER_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE, "check:" + ONE, "activate", "charge:" + ONE), events,
                "one consult (selection) + one validation, then the single part pays");

        // and the single part uncovered -> fail before consuming
        events.clear();
        swapVerdict(ONE, Affordability.insufficient(ONE, 10.0));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(SOLO_ORDER_ABILITY).outcome());
        assertEquals(List.of("check:" + ONE), events, "abort at the only part - nothing charged");
    }

    @Test
    void customStrategyScopeIsWhatGetsValidatedAndCharged() {
        // validation scope belongs to the strategy: ONE is insufficient, but
        // 'test:only_second' never selects it - so it must not refuse
        declareVerdict(ONE, Affordability.insufficient(ONE, 10.0));
        declareVerdict(TWO, sufficient(TWO));
        assertEquals(ActivationOutcome.ACTED, run(SCOPE_ABILITY).outcome());
        assertEquals(List.of("check:" + TWO, "activate", "charge:" + TWO), events,
                "the skipped entry is never consulted, validated, or charged");
    }

    @Test
    void slotAwareSelectorPicksTheEntryTheSlotStubBears() {
        // issue #90 AC's example shape (pseudocore#174): the request's slot
        // decides which entry pays - stub slot -> item enchant
        declareVerdict(ONE, sufficient(ONE));
        declareVerdict(TWO, sufficient(TWO));

        var auraOnly = pipeline.run(
                ActivationRequest.of(CUSTOM_ABILITY, ActivationSource.DIRECT_SLOT).withSlot(0), null);
        assertEquals(ActivationOutcome.ACTED, auraOnly.outcome());
        assertEquals(List.of("check:" + ONE, "activate", "charge:" + ONE), events);

        events.clear();
        var magiculeOnly = pipeline.run(
                ActivationRequest.of(CUSTOM_ABILITY, ActivationSource.DIRECT_SLOT).withSlot(1), null);
        assertEquals(ActivationOutcome.ACTED, magiculeOnly.outcome());
        assertEquals(List.of("check:" + TWO, "activate", "charge:" + TWO), events);

        events.clear();
        var bothHalves = pipeline.run(
                ActivationRequest.of(CUSTOM_ABILITY, ActivationSource.DIRECT_SLOT).withSlot(2), null);
        assertEquals(ActivationOutcome.ACTED, bothHalves.outcome());
        assertEquals(List.of("check:" + ONE, "check:" + TWO, "activate",
                "charge:" + ONE, "charge:" + TWO), events,
                "no relevant enchant: both halves pay (the default both-halves payment)");
    }

    @Test
    void strategySelectingAnUndeclaredEntryRefuses() {
        declareVerdict(ONE, sufficient(ONE));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(FABRICATE_ABILITY).outcome());
        assertTrue(events.isEmpty(),
                "a strategy may only select from the declared costs - refused before any consult");
    }

    @Test
    void strategyReturningNullRefuses() {
        declareVerdict(ONE, sufficient(ONE));
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, run(NULL_ABILITY).outcome());
        assertTrue(events.isEmpty(), "null selection is a contract violation - fail closed, nothing charged");
    }

    @Test
    void vanishedStrategyFailsClosedAtRuntime() {
        declareVerdict(ONE, sufficient(ONE));
        ConsumptionStrategies.remove("test:vanishing");
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, run(VANISHING_ABILITY).outcome());
        assertTrue(events.isEmpty(), "no strategy -> no selection -> no consult, no charge");
    }
}
