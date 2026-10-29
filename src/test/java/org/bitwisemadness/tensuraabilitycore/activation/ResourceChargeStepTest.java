package org.bitwisemadness.tensuraabilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;
import org.bitwisemadness.tensuraabilitycore.api.ActivationSource;
import org.bitwisemadness.tensuraabilitycore.api.ActivationType;
import org.bitwisemadness.tensuraabilitycore.api.Affordability;
import org.bitwisemadness.tensuraabilitycore.api.ChargingModel;
import org.bitwisemadness.tensuraabilitycore.api.ResourceConsumer;
import org.bitwisemadness.tensuraabilitycore.api.ResourceConsumers;
import org.bitwisemadness.tensuraabilitycore.api.ResourceCost;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RESOURCE-phase and consumer-contract tests (REQ-CORE-04 acceptance:
 * validate-then-charge atomicity with THE single core charge site,
 * fail-closed unknown, affordability round-trip including UNKNOWN,
 * chargingModel routing exclusivity - ADR-002).
 */
class ResourceChargeStepTest {

    private static final ResourceLocation PAID = ResourceLocation.fromNamespaceAndPath("tensura", "paid");
    private static final ResourceLocation FREE = ResourceLocation.fromNamespaceAndPath("tensura", "free");
    private static final ResourceLocation NO_CONSUMER =
            ResourceLocation.fromNamespaceAndPath("tensura", "orphan_cost");
    private static final ResourceLocation EXTERNAL_COSTLESS =
            ResourceLocation.fromNamespaceAndPath("tensura", "external_costless");
    private static final String RESOURCE = "test:pool";

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
        Registry.register(fixture, FREE, definition(null));
        Registry.register(fixture, NO_CONSUMER, definition(new ResourceCost("test:orphan", 1.0)));
        Registry.register(fixture, EXTERNAL_COSTLESS, definition(null));
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        ResourceConsumers.remove(RESOURCE);
    }

    private static AbilityDefinition definition(ResourceCost cost) {
        return new AbilityDefinition(
                "tensura.skill.x", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, cost, Set.of(), Map.of());
    }

    /** A consumer whose validate and charge both record into {@link #consumerSeen}. */
    private ResourceConsumer answering(Affordability answer) {
        return new ResourceConsumer() {
            @Override
            public Affordability validate(ResourceLocation abilityId, ServerPlayer player) {
                consumerSeen.add(answer);
                return answer;
            }

            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                consumerSeen.add(snapshot);
            }
        };
    }

    private ActivationRequest request(ResourceLocation id) {
        return ActivationRequest.of(id, ActivationSource.DIRECT_SLOT);
    }

    @Test
    void externalAbilityChargesOnceWithTheValidatedSnapshotAfterActivation() {
        ResourceConsumers.declare(RESOURCE, answering(Affordability.sufficient(RESOURCE, 10.0)));
        handlers.register(PAID, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        // validate once + charge once, and charge received THE SAME snapshot (validate-then-charge)
        assertEquals(2, consumerSeen.size());
        assertEquals(consumerSeen.get(0), consumerSeen.get(1),
                "charge must consume the validated snapshot verbatim");
        assertEquals(List.of("activate"), trace, "activation precedes the charge site");
    }

    @Test
    void insufficientNeverReachesProviderOrCharge() {
        ResourceConsumers.declare(RESOURCE, answering(Affordability.insufficient(RESOURCE, 10.0)));
        handlers.register(PAID, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertTrue(trace.isEmpty(), "provider must not run");
        assertEquals(1, consumerSeen.size(), "only validate ran - charge never invoked");
    }

    @Test
    void unknownAffordabilityFailsClosed() {
        ResourceConsumers.declare(RESOURCE, answering(Affordability.unknown(RESOURCE)));
        handlers.register(PAID, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertTrue(trace.isEmpty());
    }

    @Test
    void declaredCostWithoutConsumerFailsClosed() {
        handlers.register(NO_CONSUMER, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        var result = pipeline.run(request(NO_CONSUMER), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertEquals("ability.refused.unaffordable.no_consumer", result.effectiveMessageKey());
        assertTrue(trace.isEmpty(), "payment that cannot be validated must not activate");
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
    void consumerReturningNullFailsClosedWithLoggedContractViolation() {
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public Affordability validate(ResourceLocation abilityId, ServerPlayer player) {
                return null;
            }

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
        ResourceConsumers.declare(RESOURCE, answering(Affordability.sufficient(RESOURCE, 10.0)));
        handlers.register(PAID, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        }); // NATIVE (default)

        var result = pipeline.run(request(PAID), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(1, consumerSeen.size(), "validate ran (pre-check), core charge did NOT (NATIVE owns charging)");
        assertEquals(List.of("activate"), trace);
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
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public Affordability validate(ResourceLocation abilityId, ServerPlayer player) {
                return Affordability.sufficient(RESOURCE, 10.0);
            }

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
        ResourceConsumers.declare(RESOURCE, answering(Affordability.sufficient(RESOURCE, 1.0)));
        assertThrows(IllegalStateException.class,
                () -> ResourceConsumers.declare(RESOURCE, answering(Affordability.sufficient(RESOURCE, 1.0))));
        assertTrue(ResourceConsumers.has(RESOURCE));
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
