package org.bitwisemadness.abilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
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
import org.bitwisemadness.abilitycore.api.TargetContext;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Amount resolvers through the pipeline (issue #103): pricing precedes
 * selection (consults observe priced amounts), the fail-closed failure table
 * refuses with nothing charged, priced snapshots charge verbatim exactly
 * once, {@code static} pins today's behaviour, and the pseudocore#174 combo
 * prices from the request's resolved target while an enchantment-style
 * strategy selects the paying subset. Events are tagged
 * {@code check:id@amount} / {@code charge:id@amount} / {@code activate}.
 */
class AmountResolverTest {

    private static final ResourceLocation FAILABLE =
            ResourceLocation.fromNamespaceAndPath("tensura", "resolver_failable");
    private static final ResourceLocation CONSULTING =
            ResourceLocation.fromNamespaceAndPath("tensura", "resolver_consulting");
    private static final ResourceLocation STATIC_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "resolver_static");
    private static final ResourceLocation COMBO =
            ResourceLocation.fromNamespaceAndPath("tensura", "resolver_combo");
    private static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "skill");

    private static final String ONE = "test:one";
    private static final String TWO = "test:two";
    private static final String SWAPPABLE = "test:swappable";

    /** What the stub target resolver hands the pricing resolver (combo fixture). */
    private record StubTarget(int ep) {
    }

    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        // ELIGIBILITY first: the combo fixture needs a resolved target stashed
        // before RESOURCE runs (TargetResolver is the seam for no-world tests)
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY,
                new TargetValidationStep(new TargetResolver() {
                    @Override
                    public Object resolveEntity(UUID entityId, ServerPlayer player) {
                        return new StubTarget(30);
                    }

                    @Override
                    public boolean isLoaded(net.minecraft.core.BlockPos pos, ServerPlayer player) {
                        return true;
                    }
                }));
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        register(fixture, FAILABLE, List.of(
                new ResourceCost(ONE, 100.0), new ResourceCost(TWO, 50.0)),
                ConsumptionStrategies.ALL, SWAPPABLE);
        register(fixture, CONSULTING, List.of(new ResourceCost(ONE, 100.0)),
                ConsumptionStrategies.IN_ORDER, SWAPPABLE);
        register(fixture, STATIC_ABILITY, List.of(new ResourceCost(ONE, 10.0)),
                ConsumptionStrategies.ALL, AmountResolvers.STATIC);
        register(fixture, COMBO, List.of(new ResourceCost(ONE, 100.0),
                new ResourceCost(TWO, 100.0)), "test:slot_selector", "test:target_pricer");
        AbilityRegistries.installRegistryForTests(fixture);

        declarePair(ONE);
        declarePair(TWO);
        // enchantment-style subset selector: slot 0 pays with the first entry only
        ConsumptionStrategies.declare("test:slot_selector",
                (request, player, entries, check) -> request.slotContext() == 0
                        ? List.of(entries.get(0)) : entries);
        // prices from the resolved target: ep, ep*2 (StubTarget above)
        AmountResolvers.declare("test:target_pricer", (declared, request, player, target) -> {
            StubTarget stub = (StubTarget) target;
            return List.of(new ResourceCost(declared.get(0).resourceId(), stub.ep()),
                    new ResourceCost(declared.get(1).resourceId(), stub.ep() * 2.0));
        });

        for (ResourceLocation id : new ResourceLocation[] {FAILABLE, CONSULTING, STATIC_ABILITY, COMBO}) {
            handlers.register(id, (req, player) -> {
                events.add("activate");
                return ActivationResult.ACTED;
            }, ChargingModel.EXTERNAL);
        }
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        ResourceProviders.remove(ONE);
        ResourceConsumers.remove(ONE);
        ResourceProviders.remove(TWO);
        ResourceConsumers.remove(TWO);
        AmountResolvers.remove(SWAPPABLE);
        ConsumptionStrategies.remove("test:slot_selector");
        AmountResolvers.remove("test:target_pricer");
        events.clear();
    }

    private void register(Registry<AbilityDefinition> fixture, ResourceLocation id,
            List<ResourceCost> costs, String strategy, String resolver) {
        Registry.register(fixture, id, new AbilityDefinition(
                "tensura.skill.resolver", null, CATEGORY,
                ActivationType.INSTANT, costs, strategy, resolver, Set.of(), Map.of()));
    }

    /** Both halves record tagged events; checks always answer sufficient. */
    private void declarePair(String id) {
        ResourceProviders.declare(id, (abilityId, player, amount) -> {
            events.add("check:" + id + "@" + amount);
            return Affordability.sufficient(id, amount);
        });
        ResourceConsumers.declare(id, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                events.add("charge:" + snapshot.resourceId() + "@" + snapshot.amount());
            }
        });
    }

    private ActivationResult run(ResourceLocation ability) {
        return pipeline.run(ActivationRequest.of(ability, ActivationSource.DIRECT_SLOT), null);
    }

    /** Declares the swappable resolver for one failure-table case. */
    private void swappable(AmountResolvers.AmountResolver resolver) {
        AmountResolvers.declare(SWAPPABLE, resolver);
    }

    private void assertRefusedNothingCharged() {
        var result = run(FAILABLE);
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome());
        assertTrue(events.isEmpty(), "nothing consulted, activated or charged: " + events);
    }

    @Test
    void pricingPrecedesSelectionConsultsPricedAmounts() {
        // declared 100 -> resolver 40 -> the in_order consults observe 40,
        // validation sees 40, and the single charge receives 40 (never 100)
        swappable((declared, request, player, target) ->
                List.of(new ResourceCost(declared.get(0).resourceId(), 40.0)));

        var result = run(CONSULTING);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(4, events.size(), "consult + validation + activate + charge: " + events);
        assertTrue(events.get(0).endsWith("@40.0"), "consult saw the price: " + events.get(0));
        assertTrue(events.get(1).endsWith("@40.0"), "validation saw the price: " + events.get(1));
        assertEquals("activate", events.get(2));
        assertEquals("charge:" + ONE + "@40.0", events.get(3),
                "the consumer received the resolver's amount, verbatim, exactly once");
        assertFalse(events.stream().anyMatch(e -> e.contains("@100.0")),
                "the declared amount never reaches a consult or charge: " + events);
    }

    @Test
    void staticPassthroughChargesTheDeclaredAmount() {
        var result = run(STATIC_ABILITY);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(List.of("check:" + ONE + "@10.0", "activate", "charge:" + ONE + "@10.0"),
                events, "the default resolver is the identity - today's behaviour pinned");
    }

    @Test
    void unknownResolverNameRefuses() {
        // SWAPPABLE is deliberately not declared in this test
        assertRefusedNothingCharged();
    }

    @Test
    void vanishedResolverNameRefuses() {
        swappable((declared, request, player, target) -> declared);
        AmountResolvers.remove(SWAPPABLE);
        assertRefusedNothingCharged();
    }

    @Test
    void nullPricingRefuses() {
        swappable((declared, request, player, target) -> null);
        assertRefusedNothingCharged();
    }

    @Test
    void wrongSizePricingRefuses() {
        swappable((declared, request, player, target) -> List.of());
        assertRefusedNothingCharged();
    }

    @Test
    void reorderedResourceIdsRefuse() {
        // declared [one, two] -> returned [two, one]: same size, wrong order
        swappable((declared, request, player, target) -> List.of(
                new ResourceCost(TWO, 1.0), new ResourceCost(ONE, 1.0)));
        assertRefusedNothingCharged();
    }

    @Test
    void negativeAmountRefuses() {
        // ResourceCost's ctor rejects the value -> the throw row covers it;
        // the pipeline re-check stays as defence-in-depth
        swappable((declared, request, player, target) ->
                List.of(new ResourceCost(ONE, -1.0), new ResourceCost(TWO, 1.0)));
        assertRefusedNothingCharged();
    }

    @Test
    void notFiniteAmountRefuses() {
        swappable((declared, request, player, target) ->
                List.of(new ResourceCost(ONE, Double.NaN), new ResourceCost(TWO, 1.0)));
        assertRefusedNothingCharged();

        events.clear();
        AmountResolvers.remove(SWAPPABLE);
        swappable((declared, request, player, target) ->
                List.of(new ResourceCost(ONE, Double.POSITIVE_INFINITY), new ResourceCost(TWO, 1.0)));
        assertRefusedNothingCharged();
    }

    @Test
    void throwingResolverRefuses() {
        swappable((declared, request, player, target) -> {
            throw new IllegalStateException("resolver backend down");
        });
        assertRefusedNothingCharged();
    }

    @Test
    void everyFailureIsLoggedAsAnError() {
        // one representative row through the error-log assertion: the shape
        // violation names the resolver and the reason (the table's contract)
        swappable((declared, request, player, target) -> null);
        try (var logs = LogCapture.forLogger(ResourceChargeStep.class, Level.ERROR)) {
            var result = run(FAILABLE);
            assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome());
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains(SWAPPABLE)
                            && m.contains("invalid priced list")),
                    "the error log names resolver and reason: " + logs.messages());
        }
    }

    @Test
    void comboPricesFromResolvedTargetAndChargesOnlyTheSelectedPricedEntries() {
        // pseudocore#174's shape: EntityHint resolves to the stub target (30),
        // the resolver prices [30, 60], the enchantment-style selector picks
        // slot 0's entry - only that priced entry validates and charges
        var request = ActivationRequest.of(COMBO, ActivationSource.DIRECT_SLOT)
                .withSlot(0)
                .withTarget(new TargetContext.EntityHint(UUID.randomUUID()));

        var result = pipeline.run(request, null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(List.of("check:" + ONE + "@30.0", "activate", "charge:" + ONE + "@30.0"),
                events,
                "only the selected entry, priced from the resolved target (ep=30), verbatim");
        assertFalse(events.stream().anyMatch(e -> e.contains(TWO)),
                "the unselected entry is never consulted or charged: " + events);
        assertFalse(events.stream().anyMatch(e -> e.contains("@100.0")),
                "declared amounts never leak: " + events);
    }
}
