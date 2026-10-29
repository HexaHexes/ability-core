package org.bitwisemadness.tensuraabilitycore.activation;

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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;
import org.bitwisemadness.tensuraabilitycore.api.ActivationSource;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Order-and-guarantee tests for the activation pipeline (REQ-CORE-02's
 * acceptance: refusals short-circuit before ACTIVATION and before any charge;
 * successes charge exactly once, post-activation).
 */
class ActivationPipelineTest {

    private static final ResourceLocation KNOWN = ResourceLocation.fromNamespaceAndPath("tensura", "known");
    private static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("tensura", "unknown");

    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final List<String> trace = new ArrayList<>();

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        // fixture registry: an isolated MappedRegistry holding one definition
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, KNOWN, new AbilityDefinition(
                "tensura.skill.known", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                org.bitwisemadness.tensuraabilitycore.api.ActivationType.INSTANT,
                null, java.util.Set.of(), Map.of()));
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
    }

    private ActivationRequest request(ResourceLocation id) {
        return ActivationRequest.of(id, ActivationSource.DIRECT_SLOT);
    }

    @Test
    void unknownAbilityResolvesToUnavailableBeforeAnyStep() {
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY,
                (req, player) -> { trace.add("eligibility"); return Optional.empty(); });
        var result = pipeline.run(request(UNKNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome());
        assertTrue(trace.isEmpty(), "no step may run for an unresolvable id");
    }

    @Test
    void missingHandlerResolvesToNoAdapterBeforeEligibility() {
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY,
                (req, player) -> { trace.add("eligibility"); return Optional.empty(); });
        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_NO_ADAPTER, result.outcome());
        assertTrue(trace.isEmpty(), "eligibility must not run without an adapter");
    }

    @Test
    void eligibilityRefusalShortCircuitsEverythingAfterIt() {
        handlers.register(KNOWN, (req, player) -> { trace.add("activate"); return ActivationResult.ACTED; });
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY,
                (req, player) -> Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED)));
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE,
                (req, player) -> { trace.add("resource"); return Optional.empty(); });
        pipeline.registerCharge((req, player) -> trace.add("charge"));

        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_RESTRICTED, result.outcome());
        assertEquals(List.of(), trace, "refusal must precede resource check, activation and charge");
    }

    @Test
    void unaffordableNeverReachesProviderOrCharge() {
        handlers.register(KNOWN, (req, player) -> { trace.add("activate"); return ActivationResult.ACTED; });
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE,
                (req, player) -> Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAFFORDABLE)));
        pipeline.registerCharge((req, player) -> trace.add("charge"));

        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_UNAFFORDABLE, result.outcome());
        assertFalse(trace.contains("activate"), "provider must not run");
        assertFalse(trace.contains("charge"), "no charge after refusal");
    }

    @Test
    void cooldownRefusalOrderingIsPreserved() {
        handlers.register(KNOWN, (req, player) -> { trace.add("activate"); return ActivationResult.ACTED; });
        pipeline.registerStep(ActivationPipeline.PipelinePhase.COOLDOWN,
                (req, player) -> Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_COOLDOWN)));
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE,
                (req, player) -> { trace.add("resource"); return Optional.empty(); });

        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_COOLDOWN, result.outcome());
        assertEquals(List.of(), trace, "cooldown precedes resource and activation");
    }

    @Test
    void successChargesExactlyOnceAfterActivation() {
        handlers.register(KNOWN, (req, player) -> { trace.add("activate"); return ActivationResult.ACTED; });
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE,
                (req, player) -> { trace.add("resource"); return Optional.empty(); });
        pipeline.registerCharge((req, player) -> trace.add("charge-1"));
        pipeline.registerCharge((req, player) -> trace.add("charge-2"));

        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertTrue(result.isSuccess());
        assertEquals(List.of("resource", "activate", "charge-1", "charge-2"), trace);
    }

    @Test
    void pendingFromHandlerCountsAsSuccessAndStillCharges() {
        handlers.register(KNOWN, (req, player) -> ActivationResult.PENDING);
        pipeline.registerCharge((req, player) -> trace.add("charge"));
        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.PENDING, result.outcome());
        assertEquals(List.of("charge"), trace);
    }

    @Test
    void handlerRefusalIsReturnedVerbatimWithoutCharge() {
        handlers.register(KNOWN, (req, player) -> ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));
        pipeline.registerCharge((req, player) -> trace.add("charge"));
        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_INVALID_TARGET, result.outcome());
        assertTrue(trace.isEmpty());
    }

    @Test
    void handlerReturningNullIsAnErrorNotASuccess() {
        handlers.register(KNOWN, (req, player) -> null);
        var result = pipeline.run(request(KNOWN), null);
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome());
        assertEquals("ability.error", result.effectiveMessageKey());
    }

    @Test
    void builtInPhasesRejectStepRegistration() {
        for (var phase : new ActivationPipeline.PipelinePhase[] {
                ActivationPipeline.PipelinePhase.ACTIVATION, ActivationPipeline.PipelinePhase.CHARGE }) {
            assertThrows(IllegalArgumentException.class,
                    () -> pipeline.registerStep(phase, (req, player) -> Optional.empty()));
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

        // every outcome has a stable message key; refusals refuse success factories
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
