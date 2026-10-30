package org.bitwisemadness.abilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Order-and-guarantee tests for the activation pipeline (REQ-CORE-02's
 * acceptance plus REQ-CORE-04's chargingModel routing - ADR-002). The
 * charge-side behaviour (consumer invocation, atomicity) is covered by
 * {@link ResourceChargeStepTest}, which owns a consumer fixture.
 */
class ActivationPipelineTest {

    private static final ResourceLocation KNOWN = ResourceLocation.fromNamespaceAndPath("tensura", "known");
    private static final ResourceLocation EXTERNAL_KNOWN =
            ResourceLocation.fromNamespaceAndPath("tensura", "external_known");
    private static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("tensura", "unknown");

    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final List<String> trace = new ArrayList<>();

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, KNOWN, def());
        Registry.register(fixture, EXTERNAL_KNOWN, def());
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
    }

    private static AbilityDefinition def() {
        return new AbilityDefinition(
                "tensura.skill.known", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, null, java.util.Set.of(), Map.of());
    }

    private ActivationRequest request(ResourceLocation id) {
        return ActivationRequest.of(id, ActivationSource.DIRECT_SLOT);
    }

    private ActivationPipeline.Step tracing(String label) {
        return (req, player, context) -> {
            trace.add(label);
            return Optional.empty();
        };
    }

    @Test
    void unknownAbilityResolvesToUnavailableBeforeAnyStep() {
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, tracing("eligibility"));
        var result = pipeline.run(request(UNKNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome());
        assertTrue(trace.isEmpty(), "no step may run for an unresolvable id");
    }

    @Test
    void missingHandlerResolvesToNoAdapterBeforeEligibility() {
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, tracing("eligibility"));
        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_NO_ADAPTER, result.outcome());
        assertTrue(trace.isEmpty(), "eligibility must not run without an adapter");
    }

    @Test
    void eligibilityRefusalShortCircuitsEverythingAfterIt() {
        handlers.register(KNOWN, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        });
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY,
                (req, player, context) -> Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED)));
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, tracing("resource"));

        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, result.outcome());
        assertEquals(List.of(), trace, "refusal must precede resource check and activation");
    }

    @Test
    void unaffordableNeverReachesProvider() {
        handlers.register(KNOWN, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        });
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE,
                (req, player, context) -> Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE)));

        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertFalse(trace.contains("activate"), "provider must not run");
    }

    @Test
    void cooldownRefusalOrderingIsPreserved() {
        handlers.register(KNOWN, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        });
        pipeline.registerStep(ActivationPipeline.PipelinePhase.COOLDOWN,
                (req, player, context) -> Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_COOLDOWN)));
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, tracing("resource"));

        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_COOLDOWN, result.outcome());
        assertEquals(List.of(), trace, "cooldown precedes resource and activation");
    }

    @Test
    void activationSucceedsAndDeclarationIsStashedForChargeRouting() {
        handlers.register(KNOWN, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        });
        // EXTERNAL declaration carries its model into the context for later phases
        handlers.register(EXTERNAL_KNOWN, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);

        var nativeResult = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.ACTED, nativeResult.outcome());
        assertEquals(List.of("activate"), trace);

        // direct context probe: run a step that inspects the stashed declaration
        List<Object> seenModels = new ArrayList<>();
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY, (req, player, context) -> {
            var decl = context.get(ActivationPipeline.DECLARATION, ActivationHandlerRegistry.Declaration.class);
            decl.ifPresent(d -> seenModels.add(d.model()));
            return Optional.empty();
        });
        pipeline.run(request(EXTERNAL_KNOWN), null);
        assertEquals(List.of(ChargingModel.EXTERNAL), seenModels);
    }

    @Test
    void pendingFromHandlerCountsAsSuccess() {
        handlers.register(EXTERNAL_KNOWN, (req, player) -> ActivationResult.PENDING,
                ChargingModel.EXTERNAL);
        var result = pipeline.run(request(EXTERNAL_KNOWN), null);
        assertEquals(ActivationOutcome.PENDING, result.outcome());
        assertTrue(result.isSuccess());
    }

    @Test
    void handlerRefusalIsReturnedVerbatim() {
        handlers.register(KNOWN, (req, player) ->
                ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));
        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_INVALID_TARGET, result.outcome());
    }

    @Test
    void handlerReturningNullIsAnErrorNotASuccess() {
        handlers.register(KNOWN, (req, player) -> null);
        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome());
        assertEquals("ability.error", result.effectiveMessageKey());
    }

    @Test
    void declarationsCarryTheirChargingModel() {
        handlers.register(KNOWN, (req, player) -> ActivationResult.ACTED);
        handlers.register(EXTERNAL_KNOWN, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        assertEquals(ChargingModel.NATIVE, handlers.modelOf(KNOWN));
        assertEquals(ChargingModel.EXTERNAL, handlers.modelOf(EXTERNAL_KNOWN));
        // unregistered ids fail closed to NATIVE (gates refuse before the model matters)
        assertEquals(ChargingModel.NATIVE, handlers.modelOf(UNKNOWN));
        assertThrows(IllegalArgumentException.class,
                () -> new ActivationHandlerRegistry.Declaration(null, ChargingModel.NATIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new ActivationHandlerRegistry.Declaration((req, player) -> ActivationResult.ACTED, null));
    }

    @Test
    void builtInPhasesRejectStepRegistration() {
        for (var phase : new ActivationPipeline.PipelinePhase[] {
                ActivationPipeline.PipelinePhase.ACTIVATION, ActivationPipeline.PipelinePhase.CHARGE }) {
            assertThrows(IllegalArgumentException.class,
                    () -> pipeline.registerStep(phase, (req, player, context) -> Optional.empty()));
        }
    }

    @Test
    void duplicateHandlerRegistrationFailsLoudly() {
        handlers.register(KNOWN, (req, player) -> ActivationResult.ACTED);
        assertThrows(IllegalStateException.class,
                () -> handlers.register(KNOWN, (req, player) -> ActivationResult.ACTED));
    }

    @Test
    void wireRequestsFailClosedAndResultGuardsHold() {
        assertNull(ActivationRequest.fromWire("not a id!!", "direct_slot", -1, 0));
        assertNull(ActivationRequest.fromWire("tensura:known", "sideways", -1, 0));
        assertNotNull(ActivationRequest.fromWire("tensura:known", "direct_slot", 3, 42L));

        for (ActivationOutcome outcome : ActivationOutcome.values()) {
            assertTrue(outcome.messageKey().startsWith("ability."));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ActivationResult.refused(ActivationOutcome.ACTED));
        assertEquals("ability.refused.cooldown",
                ActivationResult.refused(ActivationOutcome.REFUSED_COOLDOWN).effectiveMessageKey());
        assertEquals("custom.key",
                new ActivationResult(ActivationOutcome.REFUSED_RESTRICTED, "custom.key").effectiveMessageKey());
    }
}
