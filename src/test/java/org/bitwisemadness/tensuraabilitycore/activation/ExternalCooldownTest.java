package org.bitwisemadness.tensuraabilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.bitwisemadness.tensuraabilitycore.ResourceConsumersDeclarations;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;
import org.bitwisemadness.tensuraabilitycore.api.ActivationSource;
import org.bitwisemadness.tensuraabilitycore.api.ActivationType;
import org.bitwisemadness.tensuraabilitycore.api.ChargingModel;
import org.bitwisemadness.tensuraabilitycore.api.CooldownOwners;
import org.bitwisemadness.tensuraabilitycore.api.CooldownTracker;
import org.bitwisemadness.tensuraabilitycore.api.ResourceCost;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cooldown ownership and pipeline integration (REQ-CORE-05 acceptance:
 * exactly one owner per ability, NATIVE untouched by core, EXTERNAL gate
 * pre-activation, start post-activation, failures logged not refused).
 */
class ExternalCooldownTest {

    private static final ResourceLocation COOLED = ResourceLocation.fromNamespaceAndPath("tensura", "cooled");
    private static final ResourceLocation NATIVE = ResourceLocation.fromNamespaceAndPath("tensura", "native_cd");
    private static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("tensura", "unknown");
    
    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final List<String> trace = new ArrayList<>();
    private final List<Long> startedDurations = new ArrayList<>();

    private long stubRemaining = 0L;

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        pipeline.registerStep(ActivationPipeline.PipelinePhase.COOLDOWN, ExternalCooldownStep.INSTANCE);
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, COOLED, def(new ResourceCost(ResourceConsumersDeclarations.RESOURCE, 10.0)));
        Registry.register(fixture, NATIVE, def(null));
        AbilityRegistries.installRegistryForTests(fixture);
        // COOLED declares a cost, so the RESOURCE phase needs a consumer: sufficient by default
        ResourceConsumersDeclarations.sufficient();
        startedDurations.clear();
        trace.clear();
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        CooldownOwners.remove(COOLED);
        CooldownOwners.remove(NATIVE);
        CooldownOwners.remove(UNKNOWN);
        ResourceConsumersDeclarations.reset();
    }

    private static AbilityDefinition def(ResourceCost cost) {
        return new AbilityDefinition(
                "tensura.skill.cd", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, cost, Set.of(), Map.of());
    }

    /** Player-null-safe stub: reports a settable remaining value, records starts. */
    private CooldownTracker stubTracker() {
        return new CooldownTracker() {
            @Override
            public long remainingTicks(ResourceLocation abilityId, net.minecraft.server.level.ServerPlayer player) {
                return stubRemaining;
            }

            @Override
            public void start(ResourceLocation abilityId, net.minecraft.server.level.ServerPlayer player,
                    long durationTicks) {
                startedDurations.add(durationTicks);
                stubRemaining = durationTicks; // started -> now cooling
            }
        };
    }

    private ActivationRequest request(ResourceLocation id) {
        return ActivationRequest.of(id, ActivationSource.DIRECT_SLOT);
    }

    @Test
    void externalCooldownStartsOnlyAfterSuccessfulActivation() {
        CooldownOwners.declareExternal(COOLED, 100L, stubTracker());
        handlers.register(COOLED, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        });

        assertTrue(startedDurations.isEmpty(), "no cooldown before any activation");
        var first = pipeline.run(request(COOLED), null);
        assertEquals(ActivationOutcome.ACTED, first.outcome());
        assertEquals(List.of(100L), startedDurations, "start happens post-activation with the declared duration");
    }

    @Test
    void coolingAbilityIsRefusedPreActivationWithoutInvokingProvider() {
        CooldownOwners.declareExternal(COOLED, 100L, stubTracker());
        handlers.register(COOLED, (req, player) -> {
            trace.add("activate");
            return ActivationResult.ACTED;
        });

        assertEquals(ActivationOutcome.ACTED, pipeline.run(request(COOLED), null).outcome());
        stubRemaining = 100L; // simulate being on cooldown

        var second = pipeline.run(request(COOLED), null);
        assertEquals(ActivationOutcome.REFUSED_COOLDOWN, second.outcome());
        assertTrue(trace.size() == 1, "provider must not run while cooling");
        assertEquals(1, startedDurations.size(), "no cooldown restart on a refused attempt");
    }

    @Test
    void nativeCooldownAbilitiesAreUntouchedByCore() {
        handlers.register(NATIVE, (req, player) -> {
            trace.add("activate-" + trace.size());
            return ActivationResult.ACTED;
        });
        // no declaration at all -> default NATIVE

        assertEquals(ActivationOutcome.ACTED, pipeline.run(request(NATIVE), null).outcome());
        assertEquals(ActivationOutcome.ACTED, pipeline.run(request(NATIVE), null).outcome());
        assertTrue(startedDurations.isEmpty(), "core never starts native cooldowns");
        assertTrue(CooldownOwners.trackerFor(NATIVE).isEmpty());
    }

    @Test
    void ownershipIsExclusiveAndFailClosed() {
        CooldownOwners.declareNative(NATIVE);
        CooldownOwners.declareExternal(COOLED, 100L, stubTracker()); // ensure present for the conflict case
        assertThrows(IllegalStateException.class,
                () -> CooldownOwners.declareExternal(NATIVE, 100L, stubTracker()),
                "dual-owner declaration must fail");
        assertThrows(IllegalStateException.class,
                () -> CooldownOwners.declareNative(COOLED));
        assertEquals(CooldownOwners.Owner.NATIVE, CooldownOwners.ownerOf(NATIVE));

        assertThrows(IllegalArgumentException.class,
                () -> CooldownOwners.declareExternal(COOLED, 0L, stubTracker()),
                "EXTERNAL requires a positive duration");
        assertThrows(IllegalArgumentException.class,
                () -> CooldownOwners.declareExternal(COOLED, -5L, stubTracker()));
        assertThrows(IllegalArgumentException.class,
                () -> CooldownOwners.declareExternal(COOLED, 100L, null),
                "EXTERNAL requires a tracker");
        // fail-closed default for undeclared ids
        assertEquals(CooldownOwners.Owner.NATIVE, CooldownOwners.ownerOf(UNKNOWN));
    }

    @Test
    void chargeFailureDoesNotPreventCooldownStart() {
        // post-activation failures are independent: the charge action throws
        // (logged), the cooldown still starts - truthfulness: ACTED is ACTED.
        CooldownOwners.declareExternal(COOLED, 100L, stubTracker());
        handlers.register(COOLED, (req, player) -> ActivationResult.ACTED, ChargingModel.EXTERNAL);
        ResourceConsumersDeclarations.throwing();

        var result = pipeline.run(request(COOLED), null);
        assertEquals(ActivationOutcome.ACTED, result.outcome());
        assertEquals(List.of(100L), startedDurations, "cooldown start is independent of charge failure");
    }

    @Test
    void refusedActivationDoesNotStartExternalCooldown() {
        CooldownOwners.declareExternal(COOLED, 100L, stubTracker());
        handlers.register(COOLED, (req, player) ->
                ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));

        var result = pipeline.run(request(COOLED), null);
        assertEquals(ActivationOutcome.REFUSED_INVALID_TARGET, result.outcome());
        assertTrue(startedDurations.isEmpty(), "a refused activation never starts a cooldown");
    }

    @Test
    void declarationsSurviveIndependentRemove() {
        CooldownOwners.declareExternal(COOLED, 100L, stubTracker());
        assertTrue(CooldownOwners.remove(COOLED));
        assertFalse(CooldownOwners.remove(COOLED), "second removal is a no-op");
        assertEquals(CooldownOwners.Owner.NATIVE, CooldownOwners.ownerOf(COOLED));
    }
}
