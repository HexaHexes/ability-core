package org.bitwisemadness.abilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.BuiltinResources;
import org.bitwisemadness.abilitycore.LogCapture;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RESOURCE-phase and provider/consumer-contract tests (REQ-CORE-04
 * acceptance, provider/consumer split per issue #88: validate-then-charge
 * atomicity with THE single core charge site, fail-closed unknown,
 * affordability round-trip including UNKNOWN, chargingModel routing
 * exclusivity - ADR-002).
 */
class ResourceChargeStepTest {

    private static final ResourceLocation PAID = ResourceLocation.fromNamespaceAndPath("tensura", "paid");
    private static final ResourceLocation FREE = ResourceLocation.fromNamespaceAndPath("tensura", "free");
    private static final ResourceLocation NO_CONSUMER =
            ResourceLocation.fromNamespaceAndPath("tensura", "orphan_cost");
    private static final ResourceLocation EXTERNAL_COSTLESS =
            ResourceLocation.fromNamespaceAndPath("tensura", "external_costless");
    private static final ResourceLocation FREE_ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "deliberately_free");
    private static final ResourceLocation MULTI =
            ResourceLocation.fromNamespaceAndPath("tensura", "multi_paid");
    private static final ResourceLocation MIXED_VALID_FREE =
            ResourceLocation.fromNamespaceAndPath("tensura", "valid_and_free");
    private static final ResourceLocation MIXED_INVALID_FREE =
            ResourceLocation.fromNamespaceAndPath("tensura", "invalid_and_free");
    private static final String RESOURCE = "test:pool";
    private static final String SECOND = "test:second";
    private static final String ORPHAN = "test:orphan";

    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final List<String> trace = new ArrayList<>();
    private final List<Affordability> consumerSeen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, ResourceChargeStep.INSTANCE);
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, PAID, definition(new ResourceCost(RESOURCE, 10.0)));
        Registry.register(fixture, FREE, definition(List.of()));
        Registry.register(fixture, NO_CONSUMER, definition(new ResourceCost(ORPHAN, 1.0)));
        Registry.register(fixture, EXTERNAL_COSTLESS, definition(List.of()));
        Registry.register(fixture, FREE_ABILITY, definition(new ResourceCost(BuiltinResources.FREE, 0.0)));
        Registry.register(fixture, MULTI, definition(List.of(
                new ResourceCost(RESOURCE, 10.0), new ResourceCost(SECOND, 5.0))));
        Registry.register(fixture, MIXED_VALID_FREE, definition(List.of(
                new ResourceCost(RESOURCE, 10.0), new ResourceCost(BuiltinResources.FREE, 0.0))));
        Registry.register(fixture, MIXED_INVALID_FREE, definition(List.of(
                new ResourceCost(ORPHAN, 1.0), new ResourceCost(BuiltinResources.FREE, 0.0))));
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        ResourceConsumers.remove(RESOURCE);
        ResourceProviders.remove(RESOURCE);
        ResourceConsumers.remove(SECOND);
        ResourceProviders.remove(SECOND);
        ResourceConsumers.remove(ORPHAN);
        ResourceProviders.remove(ORPHAN);
        for (String id : new String[] {
                BuiltinResources.HEALTH, BuiltinResources.HUNGER,
                BuiltinResources.EXPERIENCE_POINTS, BuiltinResources.EXPERIENCE_LEVELS,
                BuiltinResources.FREE }) {
            ResourceConsumers.remove(id);
            ResourceProviders.remove(id);
        }
    }

    private static AbilityDefinition definition(ResourceCost cost) {
        return new AbilityDefinition(
                "tensura.skill.x", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, cost == null ? List.of() : List.of(cost), Set.of(), Map.of());
    }

    /** Multi-entry variant (issue #89): costs validated all-or-nothing. */
    private static AbilityDefinition definition(List<ResourceCost> costs) {
        return new AbilityDefinition(
                "tensura.skill.x", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, costs, Set.of(), Map.of());
    }

    /**
     * Declares the answering pair: the provider's check records the answer,
     * the consumer's charge records the snapshot it received - both into
     * {@link #consumerSeen}, so the fixtures count halves independently.
     */
    private void declareAnswering(Affordability answer) {
        ResourceProviders.declare(RESOURCE, (abilityId, player, amount) -> {
            consumerSeen.add(answer);
            return answer;
        });
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                consumerSeen.add(snapshot);
            }
        });
    }

    private ActivationRequest request(ResourceLocation id) {
        return ActivationRequest.of(id, ActivationSource.DIRECT_SLOT);
    }

    /** Declares a recording pair: its check answer and its charge both land in {@link #consumerSeen}. */
    private void declareRecordingPair(String id) {
        ResourceProviders.declare(id, (abilityId, player, amount) -> {
            var answer = Affordability.sufficient(id, amount);
            consumerSeen.add(answer);
            return answer;
        });
        ResourceConsumers.declare(id, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                consumerSeen.add(snapshot);
            }
        });
    }

    @Test
    void externalAbilityChargesOnceWithTheValidatedSnapshotAfterActivation() {
        declareAnswering(Affordability.sufficient(RESOURCE, 10.0));
        handlers.register(PAID, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        // check once + charge once, and charge received THE SAME snapshot (validate-then-charge)
        assertEquals(2, consumerSeen.size());
        assertEquals(consumerSeen.get(0), consumerSeen.get(1),
                "charge must consume the validated snapshot verbatim");
        assertEquals(List.of("activate"), trace, "activation precedes the charge site");
    }

    @Test
    void insufficientNeverReachesProviderOrCharge() {
        declareAnswering(Affordability.insufficient(RESOURCE, 10.0));
        handlers.register(PAID, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertTrue(trace.isEmpty(), "activation must not run");
        assertEquals(1, consumerSeen.size(), "only the check ran - charge never invoked");
    }

    @Test
    void unknownAffordabilityFailsClosed() {
        declareAnswering(Affordability.unknown(RESOURCE));
        handlers.register(PAID, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertTrue(trace.isEmpty());
    }

    @Test
    void declaredCostWithoutProviderFailsClosed() {
        // neither half declared: the read half is checked first (issue #88)
        handlers.register(NO_CONSUMER, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        var result = pipeline.run(request(NO_CONSUMER), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertEquals("ability.refused.unaffordable.no_provider", result.effectiveMessageKey());
        assertTrue(trace.isEmpty(), "payment that cannot be validated must not activate");
    }

    @Test
    void declaredCostWithoutConsumerFailsClosed() {
        // provider present (validation possible) but no write half to charge
        ResourceProviders.declare(ORPHAN, (abilityId, player, amount) ->
                Affordability.sufficient(ORPHAN, amount));
        handlers.register(NO_CONSUMER, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        var result = pipeline.run(request(NO_CONSUMER), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertEquals("ability.refused.unaffordable.no_consumer", result.effectiveMessageKey());
        assertTrue(trace.isEmpty(), "payment that cannot be charged must not activate");
    }

    @Test
    void externalWithoutDeclaredCostIsRefusedPreActivation() {
        handlers.register(EXTERNAL_COSTLESS, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        var result = pipeline.run(request(EXTERNAL_COSTLESS), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertEquals("ability.refused.unaffordable.undeclared", result.effectiveMessageKey());
        assertTrue(trace.isEmpty(), "the single charge site needs a declared cost - refused before activation");
    }

    @Test
    void deliberateFreeCostActsAtNoCost() {
        // issue #93: naming ability_core:free IS the explicit act - the pair
        // validates at 0, the no-op charge runs, the ability ACTs (the
        // cost == null control just above still refuses undeclared).
        BuiltinResources.registerAll();
        handlers.register(FREE_ABILITY, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        try (LogCapture logs = LogCapture.forLogger(BuiltinResources.class, Level.DEBUG)) {
            var result = pipeline.run(request(FREE_ABILITY), null);
            assertEquals(ActivationOutcome.ACTED, result.outcome(),
                    "a declared free cost routes through the charge site and passes");
            assertEquals(List.of("activate"), trace, "activation ran; nothing else interfered");
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains("charged nothing")),
                    "the no-op charge is visible in the debug log: " + logs.messages());
        }
    }

    @Test
    void multiEntryCostValidatesAllThenChargesEachSnapshotOnceInListOrder() {
        // issue #89: checks run in list order pre-activation; charges run
        // post-ACTED in the same list order, each exactly once
        declareRecordingPair(RESOURCE);
        declareRecordingPair(SECOND);
        handlers.register(MULTI, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(MULTI), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(List.of("activate"), trace, "validation precedes activation");
        assertEquals(4, consumerSeen.size(), "2 checks + 2 charges");
        assertEquals(List.of(RESOURCE, SECOND, RESOURCE, SECOND),
                consumerSeen.stream().map(Affordability::resourceId).toList(),
                "checks then charges, each in declaration order");
        assertEquals(List.of(10.0, 5.0, 10.0, 5.0),
                consumerSeen.stream().map(Affordability::amount).toList());
        assertEquals(consumerSeen.get(0), consumerSeen.get(2),
                "RESOURCE charged its own validated snapshot verbatim");
        assertEquals(consumerSeen.get(1), consumerSeen.get(3),
                "SECOND charged its own validated snapshot verbatim");
    }

    @Test
    void invalidLaterEntryRefusesWithoutChargingAnyEntry() {
        // issue #89 all-or-nothing: entry 1's successful check must not
        // survive entry 2's failure (SECOND has no provider here)
        declareRecordingPair(RESOURCE);
        handlers.register(MULTI, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(MULTI), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertEquals("ability.refused.unaffordable.no_provider", result.effectiveMessageKey());
        assertTrue(trace.isEmpty(), "nothing activates");
        assertEquals(1, consumerSeen.size(),
                "only entry 1's check ran - no charges, nothing stashed");
        assertEquals(RESOURCE, consumerSeen.get(0).resourceId());
    }

    @Test
    void validEntryPaysWhileFreeSiblingRunsItsNoOp() {
        // composition rule (#93/#89): [valid, free] - the valid entry is the
        // payment and gets charged; the free sibling validates and no-ops
        BuiltinResources.registerAll();
        declareRecordingPair(RESOURCE);
        handlers.register(MIXED_VALID_FREE, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        try (LogCapture logs = LogCapture.forLogger(BuiltinResources.class, Level.DEBUG)) {
            var result = pipeline.run(request(MIXED_VALID_FREE), null);
            assertEquals(ActivationOutcome.ACTED, result.outcome());
            assertEquals(List.of("activate"), trace);
            assertEquals(2, consumerSeen.size(),
                    "RESOURCE check + RESOURCE charge (the free pair records nothing here)");
            assertEquals(RESOURCE, consumerSeen.get(0).resourceId());
            assertEquals(RESOURCE, consumerSeen.get(1).resourceId());
            assertEquals(consumerSeen.get(0), consumerSeen.get(1),
                    "the validated snapshot is what got charged");
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains("charged nothing")),
                    "the free sibling charged its no-op: " + logs.messages());
        }
    }

    @Test
    void freeSiblingNeverMasksAnInvalidEntry() {
        // composition rule: [invalid, free] refuses - never a free-success
        BuiltinResources.registerAll();
        handlers.register(MIXED_INVALID_FREE, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        try (LogCapture logs = LogCapture.forLogger(BuiltinResources.class, Level.DEBUG)) {
            var result = pipeline.run(request(MIXED_INVALID_FREE), null);
            assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
            assertEquals("ability.refused.unaffordable.no_provider", result.effectiveMessageKey());
            assertTrue(trace.isEmpty(), "free must never turn an invalid sibling into a success");
            assertTrue(consumerSeen.isEmpty(), "nothing validated past the refusal, nothing charged");
            assertTrue(logs.messages().stream().noneMatch(m -> m.contains("charged nothing")),
                    "the free consumer never ran: " + logs.messages());
        }
    }

    @Test
    void providerReturningNullFailsClosedWithLoggedContractViolation() {
        ResourceProviders.declare(RESOURCE, (abilityId, player, amount) -> null);
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                // never reached
            }
        });
        handlers.register(PAID, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertTrue(trace.isEmpty());
    }

    @Test
    void nativeAbilityWithCostValidatesButNeverChargesViaCore() {
        declareAnswering(Affordability.sufficient(RESOURCE, 10.0));
        handlers.register(PAID, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }); // NATIVE (default)

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(1, consumerSeen.size(), "check ran (pre-check), core charge did NOT (NATIVE owns charging)");
        assertEquals(List.of("activate"), trace);
    }

    @Test
    void nativeAbilityDeductsExactlyOnceThroughItsOwnSite() {
        // REQ-CORE-04 AC2 (mock half): one NATIVE activation = exactly one
        // provider-side deduction, core's consumer sees only the pre-check.
        // The ISS-backed observation is a release-time dev-run check (#30).
        declareAnswering(Affordability.sufficient(RESOURCE, 10.0));
        AtomicInteger deductions = new AtomicInteger();
        handlers.register(PAID, (req, player) -> {
            deductions.incrementAndGet(); // the provider's own charge site
            return ActivationResult.ACTED;
        }); // NATIVE

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(1, deductions.get(), "exactly one deduction per activation");
        assertEquals(1, consumerSeen.size(),
                "core validated only - a second deduction site must not exist");
    }

    @Test
    void handlerRefusalNeverCharges() {
        // REQ-CORE-04 AC3: the handler refuses AFTER validation, so the
        // charge site (post-ACTED) must not fire. externalAbilityChargesOnce...
        // is the paired control proving this charge arm fires on success.
        declareAnswering(Affordability.sufficient(RESOURCE, 10.0));
        handlers.register(PAID, (req, player) -> {
            trace.add("activate");
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, result.outcome());
        assertEquals(List.of("activate"), trace, "the handler ran and refused");
        assertEquals(1, consumerSeen.size(),
                "check ran, charge never did: a failed activation consumes nothing");
    }

    @Test
    void costlessNativeAbilitySkipsResourceValidationEntirely() {
        handlers.register(FREE, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        });
        var result = pipeline.run(request(FREE), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(List.of("activate"), trace);
        assertTrue(consumerSeen.isEmpty());
    }

    @Test
    void chargeFailureAfterActivationIsLoggedNotRefused() {
        ResourceProviders.declare(RESOURCE, (abilityId, player, amount) ->
                Affordability.sufficient(RESOURCE, amount));
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                throw new IllegalStateException("charge backend down");
            }
        });
        handlers.register(PAID, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        var result = pipeline.run(request(PAID), null);
        // post-activation: cannot un-act; success is reported truthfully, failure logged
        assertEquals(ActivationOutcome.ACTED, result.outcome());
    }

    @Test
    void resourceOwnershipIsExclusivePerCurrency() {
        declareAnswering(Affordability.sufficient(RESOURCE, 1.0));
        assertThrows(IllegalStateException.class,
                () -> ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
                    @Override
                    public void charge(ResourceLocation abilityId, ServerPlayer player,
                            Affordability snapshot) {
                        // duplicate - must be rejected before this runs
                    }
                }));
        assertThrows(IllegalStateException.class,
                () -> ResourceProviders.declare(RESOURCE, (abilityId, player, amount) ->
                        Affordability.sufficient(RESOURCE, amount)));
        assertTrue(ResourceConsumers.has(RESOURCE));
        assertTrue(ResourceProviders.has(RESOURCE));
        assertTrue(ResourceConsumers.remove(RESOURCE));
    }

    @Test
    void affordabilityGuardsAndFactories() {
        assertEquals(Affordability.State.SUFFICIENT, Affordability.sufficient("a:b", 1).state());
        assertEquals(Boolean.TRUE, Affordability.sufficient("a:b", 1).sufficientFlag());
        assertEquals(Boolean.FALSE, Affordability.insufficient("a:b", 1).sufficientFlag());
        assertEquals(null, Affordability.unknown("a:b").sufficientFlag());
        assertEquals(0.0, Affordability.unknown("a:b").amount());
        assertThrows(IllegalArgumentException.class, () -> new Affordability("", 1, Affordability.State.SUFFICIENT));
        assertThrows(IllegalArgumentException.class, () -> new Affordability("a:b", -1, Affordability.State.SUFFICIENT));
        assertThrows(IllegalArgumentException.class, () -> new Affordability("a:b", 1, null));
    }
}
