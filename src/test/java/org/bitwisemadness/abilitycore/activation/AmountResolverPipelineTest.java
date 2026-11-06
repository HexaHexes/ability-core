package org.bitwisemadness.abilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
 * Resolution-point pipeline fixtures (REQ-CORE-04 p4, issue #103):
 * pricing runs after the empty-costs gate and before selection (consults
 * observe priced amounts), priced snapshots charge verbatim, every
 * mirror-contract violation refuses fail-closed with nothing charged,
 * the resolved target reaches the resolver, and the combo with a subset
 * strategy prices exactly what gets charged.
 */
class AmountResolverPipelineTest {

    private static final ResourceLocation PRICED =
            ResourceLocation.fromNamespaceAndPath("tensura", "priced");
    private static final ResourceLocation STATIC_ONE =
            ResourceLocation.fromNamespaceAndPath("tensura", "static_priced");
    private static final ResourceLocation WRONG_SIZE =
            ResourceLocation.fromNamespaceAndPath("tensura", "wrong_size");
    private static final ResourceLocation REORDERED =
            ResourceLocation.fromNamespaceAndPath("tensura", "reordered");
    private static final ResourceLocation NEGATIVE =
            ResourceLocation.fromNamespaceAndPath("tensura", "negative");
    private static final ResourceLocation NAN_AMOUNT =
            ResourceLocation.fromNamespaceAndPath("tensura", "nan_amount");
    private static final ResourceLocation INF_AMOUNT =
            ResourceLocation.fromNamespaceAndPath("tensura", "infinite_amount");
    private static final ResourceLocation NULL_RESULT =
            ResourceLocation.fromNamespaceAndPath("tensura", "null_result");
    private static final ResourceLocation THROWING =
            ResourceLocation.fromNamespaceAndPath("tensura", "throwing");
    private static final ResourceLocation VANISHED =
            ResourceLocation.fromNamespaceAndPath("tensura", "vanished");
    private static final ResourceLocation COMBO =
            ResourceLocation.fromNamespaceAndPath("tensura", "combo");
    private static final ResourceLocation TARGETED =
            ResourceLocation.fromNamespaceAndPath("tensura", "targeted");

    private static final String RESOURCE = "test:pool";
    private static final String SECOND = "test:second";
    private static final String ONLY_SECOND = "test:only_second";
    private static final String[] TEST_RESOLVERS = {
        "test:fixed40", "test:too_short", "test:swapped", "test:negative",
        "test:nan", "test:infinite", "test:null", "test:boom",
        "test:vanishing", "test:double", "test:target_probe"
    };

    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final List<String> trace = new ArrayList<>();
    private final List<Affordability> seen = new ArrayList<>();
    private final List<Object> seenTargets = new ArrayList<>();
    private final Object resolvedSentinel = new Object();

    private final TargetResolver fakeResolver = new TargetResolver() {
        @Override
        public Object resolveEntity(UUID entityId, ServerPlayer player) {
            return resolvedSentinel;
        }

        @Override
        public boolean isLoaded(BlockPos pos, ServerPlayer player) {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);

        // addon-shaped construction-time declarations (the timing contract)
        AmountResolvers.declare("test:fixed40",
                (declared, request, player, target) -> List.of(new ResourceCost(RESOURCE, 40.0)));
        AmountResolvers.declare("test:too_short",
                (declared, request, player, target) -> List.of(declared.get(0)));
        AmountResolvers.declare("test:swapped", (declared, request, player, target) -> List.of(
                new ResourceCost(SECOND, 1.0), new ResourceCost(RESOURCE, 2.0)));
        AmountResolvers.declare("test:negative",
                (declared, request, player, target) -> List.of(new ResourceCost(RESOURCE, -5.0)));
        AmountResolvers.declare("test:nan",
                (declared, request, player, target) -> List.of(new ResourceCost(RESOURCE, Double.NaN)));
        AmountResolvers.declare("test:infinite", (declared, request, player, target) ->
                List.of(new ResourceCost(RESOURCE, Double.POSITIVE_INFINITY)));
        AmountResolvers.declare("test:null",
                (declared, request, player, target) -> null);
        AmountResolvers.declare("test:boom", (declared, request, player, target) -> {
            throw new IllegalStateException("resolver backend down");
        });
        AmountResolvers.declare("test:vanishing",
                (declared, request, player, target) -> declared);
        AmountResolvers.declare("test:double", (declared, request, player, target) -> List.of(
                new ResourceCost(declared.get(0).resourceId(), declared.get(0).amount() * 2.0),
                new ResourceCost(declared.get(1).resourceId(), declared.get(1).amount() * 2.0)));
        AmountResolvers.declare("test:target_probe", (declared, request, player, target) -> {
            seenTargets.add(target);
            return declared;
        });
        ConsumptionStrategies.declare(ONLY_SECOND,
                (request, player, entries, check) -> List.of(entries.get(1)));

        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, PRICED, definition(
                oneCost(100.0), ConsumptionStrategies.IN_ORDER, "test:fixed40"));
        Registry.register(fixture, STATIC_ONE, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, AmountResolvers.STATIC));
        Registry.register(fixture, WRONG_SIZE, definition(
                twoCosts(), ConsumptionStrategies.ALL, "test:too_short"));
        Registry.register(fixture, REORDERED, definition(
                twoCosts(), ConsumptionStrategies.ALL, "test:swapped"));
        Registry.register(fixture, NEGATIVE, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, "test:negative"));
        Registry.register(fixture, NAN_AMOUNT, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, "test:nan"));
        Registry.register(fixture, INF_AMOUNT, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, "test:infinite"));
        Registry.register(fixture, NULL_RESULT, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, "test:null"));
        Registry.register(fixture, THROWING, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, "test:boom"));
        Registry.register(fixture, VANISHED, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, "test:vanishing"));
        Registry.register(fixture, COMBO, definition(
                twoCosts(), ONLY_SECOND, "test:double"));
        Registry.register(fixture, TARGETED, definition(
                oneCost(100.0), ConsumptionStrategies.ALL, "test:target_probe"));
        AbilityRegistries.installRegistryForTests(fixture);

        for (ResourceLocation id : List.of(PRICED, STATIC_ONE, WRONG_SIZE, REORDERED, NEGATIVE,
                NAN_AMOUNT, INF_AMOUNT, NULL_RESULT, THROWING, VANISHED, COMBO, TARGETED)) {
            handlers.register(id, (req, player) -> {
                trace.add("activate");
                return ActivationResult.ACTED;
            }, ChargingModel.EXTERNAL);
        }
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        for (String resolver : TEST_RESOLVERS) {
            AmountResolvers.remove(resolver);
        }
        ConsumptionStrategies.remove(ONLY_SECOND);
        ResourceConsumers.remove(RESOURCE);
        ResourceProviders.remove(RESOURCE);
        ResourceConsumers.remove(SECOND);
        ResourceProviders.remove(SECOND);
        trace.clear();
        seen.clear();
        seenTargets.clear();
    }

    private static List<ResourceCost> oneCost(double amount) {
        return List.of(new ResourceCost(RESOURCE, amount));
    }

    private static List<ResourceCost> twoCosts() {
        return List.of(new ResourceCost(RESOURCE, 100.0), new ResourceCost(SECOND, 50.0));
    }

    private static AbilityDefinition definition(List<ResourceCost> costs, String strategy,
            String resolver) {
        return new AbilityDefinition(
                "tensura.skill.x", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, costs, strategy, resolver, Set.of(), Map.of());
    }

    private static ActivationRequest request(ResourceLocation id) {
        return ActivationRequest.of(id, ActivationSource.DIRECT_SLOT);
    }

    /** Declares a recording pair: its check answer and its charge both land in {@link #seen}. */
    private void declareRecordingPair(String id) {
        ResourceProviders.declare(id, (abilityId, player, amount) -> {
            var answer = Affordability.sufficient(id, amount);
            seen.add(answer);
            return answer;
        });
        ResourceConsumers.declare(id, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                seen.add(snapshot);
            }
        });
    }

    private void assertRefusedFailClosed(ResourceLocation id, String logNeedle) {
        try (LogCapture logs = LogCapture.forLogger(ResourceChargeStep.class, Level.ERROR)) {
            var result = pipeline.run(request(id), null);
            assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome(), id.toString());
            assertTrue(trace.isEmpty(), "nothing activates: " + id);
            assertTrue(seen.isEmpty(), "nothing validated or charged: " + id);
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains(logNeedle)),
                    "expected a '" + logNeedle + "' log for " + id + ": " + logs.messages());
        }
    }

    @Test
    void pricingRunsBeforeSelectionSoConsultsSeeThePricedAmount() {
        declareRecordingPair(RESOURCE);
        var result = pipeline.run(request(PRICED), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        // in_order consulted the priced 40 (never the declared 100), the
        // fail-closed re-check priced it again, and the charge got that snapshot
        assertEquals(3, seen.size(), "consult + validation + charge");
        assertEquals(List.of(40.0, 40.0, 40.0),
                seen.stream().map(Affordability::amount).toList(),
                "every consult and charge observes the resolver's amount");
        assertEquals(seen.get(1), seen.get(2),
                "charge received the validated snapshot verbatim");
        assertEquals(List.of("activate"), trace, "activation runs after validation");
    }

    @Test
    void theStaticResolverKeepsTheDeclaredAmounts() {
        // regression: static skips dispatch, so pre-#103 behavior is pinned
        declareRecordingPair(RESOURCE);
        var result = pipeline.run(request(STATIC_ONE), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(2, seen.size(), "validation + charge (all selects without consulting)");
        assertEquals(List.of(100.0, 100.0),
                seen.stream().map(Affordability::amount).toList());
        assertEquals(seen.get(0), seen.get(1), "verbatim snapshot, exactly as before #103");
    }

    @Test
    void aWrongSizedResultRefusesFailClosed() {
        assertRefusedFailClosed(WRONG_SIZE, "violated its contract");
    }

    @Test
    void reorderedResourceIdsRefuseFailClosed() {
        assertRefusedFailClosed(REORDERED, "violated its contract");
    }

    @Test
    void aResolverBuildingANegativeAmountIsCaughtAndRefuses() {
        // ResourceCost's own guard rejects < 0 inside the resolver, so the
        // throw row owns it: same fail-closed outcome, never a priced entry
        assertRefusedFailClosed(NEGATIVE, "threw - refusing");
    }

    @Test
    void aResolverBuildingNonFiniteAmountsIsCaughtAndRefuses() {
        // same as the negative row: ResourceCost rejects NaN/Infinity at
        // construction inside the resolver - caught at the resolution point
        assertRefusedFailClosed(NAN_AMOUNT, "threw - refusing");
        assertRefusedFailClosed(INF_AMOUNT, "threw - refusing");
    }

    @Test
    void aNullResultRefusesFailClosed() {
        assertRefusedFailClosed(NULL_RESULT, "violated its contract");
    }

    @Test
    void aThrowingResolverIsCaughtAndRefuses() {
        assertRefusedFailClosed(THROWING, "threw - refusing");
    }

    @Test
    void aVanishedResolverNameRefuses() {
        assertTrue(AmountResolvers.remove("test:vanishing"), "the name existed at registration");
        assertRefusedFailClosed(VANISHED, "unknown amount resolver");
    }

    @Test
    void comboOnlyTheSelectedPricedEntryValidatesAndCharges() {
        // resolver prices both entries (x2), the subset strategy picks the
        // second: only it validates and charges, at the priced amount
        declareRecordingPair(RESOURCE);
        declareRecordingPair(SECOND);
        var result = pipeline.run(request(COMBO), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(List.of(SECOND, SECOND),
                seen.stream().map(Affordability::resourceId).toList(),
                "the selected entry only - the skipped one is never consulted");
        assertEquals(List.of(100.0, 100.0),
                seen.stream().map(Affordability::amount).toList(),
                "declared 50, priced x2 = 100 charged");
        assertEquals(seen.get(0), seen.get(1), "validated snapshot charged verbatim");
    }

    @Test
    void theResolvedEntityHintReachesTheResolverOrNullWithoutOne() {
        declareRecordingPair(RESOURCE);
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY,
                new TargetValidationStep(fakeResolver));

        var withHint = pipeline.run(
                request(TARGETED).withTarget(new TargetContext.EntityHint(UUID.randomUUID())), null);
        assertEquals(ActivationOutcome.ACTED, withHint.outcome());
        assertEquals(1, seenTargets.size());
        assertSame(resolvedSentinel, seenTargets.get(0),
                "the server-stashed RESOLVED_ENTITY value is what the resolver receives");

        var withoutHint = pipeline.run(request(TARGETED), null);
        assertEquals(ActivationOutcome.ACTED, withoutHint.outcome());
        assertEquals(2, seenTargets.size());
        assertNull(seenTargets.get(1), "no entity hint resolves to a null target");
    }
}
