package org.bitwisemadness.tensuraabilitycore.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import org.bitwisemadness.tensuraabilitycore.api.TargetContext;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Target validation and resolution (REQ-CORE-06 acceptance): accepted-kinds
 * gate (absent key = provider authority), server-side resolution with
 * REFUSED_INVALID_TARGET for unresolvable hints, resolved-value stash.
 *
 * <p>Pipeline note exercised throughout: ADAPTER precedes ELIGIBILITY, so a
 * passing target gate surfaces as REFUSED_NO_ADAPTER when no handler is
 * registered - the gate provably ran.</p>
 *
 * <p>Resolved values are opaque test sentinels here: core never dereferences
 * them, so unit tests need no live Minecraft classes (the production default
 * resolver returns real entities via the requester's ServerLevel).</p>
 */
class TargetValidationStepTest {

    private static final ResourceLocation ANY_KIND =
            ResourceLocation.fromNamespaceAndPath("tensura", "any_kind");
    private static final ResourceLocation BLOCK_ONLY =
            ResourceLocation.fromNamespaceAndPath("tensura", "block_only");
    private static final ResourceLocation UNKNOWN =
            ResourceLocation.fromNamespaceAndPath("tensura", "unknown");

    private ActivationHandlerRegistry handlers;
    private ActivationPipeline pipeline;
    private Registry<AbilityDefinition> previousRegistry;
    private final UUID entityId = UUID.randomUUID();
    private final Object resolvedSentinel = new Object();
    private boolean resolved = true;
    private boolean blockLoaded = true;

    /** Fake resolver: lookups answered by test fields, no Minecraft classes touched. */
    private final TargetResolver fakeResolver = new TargetResolver() {
        @Override
        public Object resolveEntity(UUID id, ServerPlayer player) {
            return resolved ? resolvedSentinel : null;
        }

        @Override
        public boolean isLoaded(net.minecraft.core.BlockPos pos, ServerPlayer player) {
            return blockLoaded;
        }
    };

    @BeforeEach
    void setUp() {
        handlers = new ActivationHandlerRegistry();
        pipeline = new ActivationPipeline(handlers);
        pipeline.registerStep(ActivationPipeline.PipelinePhase.ELIGIBILITY,
                new TargetValidationStep(fakeResolver));
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, ANY_KIND, definition(Map.of()));
        Registry.register(fixture, BLOCK_ONLY, definition(
                Map.of(TargetValidationStep.ACCEPTED_TARGETS_KEY, "block")));
        handlers.register(ANY_KIND, (req, player) -> ActivationResult.ACTED);
        handlers.register(BLOCK_ONLY, (req, player) -> ActivationResult.ACTED);
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
    }

    private static AbilityDefinition definition(Map<String, String> data) {
        return new AbilityDefinition(
                "tensura.skill.targeted", null,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, null, Set.of(), data);
    }

    private ActivationRequest request(ResourceLocation id, TargetContext target) {
        return ActivationRequest.of(id, ActivationSource.DIRECT_SLOT).withTarget(target);
    }

    @Test
    void absentAcceptedKeyMeansProviderRemainsTheAuthority() {
        resolved = false;
        // self/none pass the gate and proceed (handler exists -> ACTED):
        // the gate does not refuse, i.e. the provider remains the authority
        var selfResult = pipeline.run(request(ANY_KIND, new TargetContext.Self()), null);
        assertEquals(ActivationOutcome.ACTED, selfResult.outcome());
        var noneResult = pipeline.run(request(ANY_KIND, new TargetContext.None()), null);
        assertEquals(ActivationOutcome.ACTED, noneResult.outcome());
    }

    @Test
    void unresolvableEntityHintIsRefusedBeforeTheAdapterGate() {
        resolved = false;
        var result = pipeline.run(request(ANY_KIND, new TargetContext.EntityHint(entityId)), null);
        assertEquals(ActivationOutcome.REFUSED_INVALID_TARGET, result.outcome());
        assertEquals("ability.refused.invalid_target", result.effectiveMessageKey());
    }

    @Test
    void entityHintResolvesAndStashesTheValueForProviders() {
        List<Object> seen = new ArrayList<>();
        pipeline.registerStep(ActivationPipeline.PipelinePhase.RESOURCE, (req, player, context) -> {
            seen.add(context.get(TargetValidationStep.RESOLVED_ENTITY, Object.class).orElseThrow());
            return Optional.empty();
        });

        var result = pipeline.run(request(ANY_KIND, new TargetContext.EntityHint(entityId)), null);
        assertTrue(result.isSuccess());
        assertEquals(1, seen.size());
        assertSame(resolvedSentinel, seen.get(0), "providers consume the server-resolved value, not the hint");
    }

    @Test
    void unloadedBlockHintIsRefused() {
        blockLoaded = false;
        var result = pipeline.run(request(ANY_KIND,
                new TargetContext.BlockHint(new BlockPos(0, -60, 0))), null);
        assertEquals(ActivationOutcome.REFUSED_INVALID_TARGET, result.outcome());
    }

    @Test
    void acceptedKindsGateRefusesMismatchedHints() {
        var refused = pipeline.run(request(BLOCK_ONLY, new TargetContext.Self()), null);
        assertEquals(ActivationOutcome.REFUSED_INVALID_TARGET, refused.outcome());
        assertEquals(ActivationOutcome.REFUSED_INVALID_TARGET,
                pipeline.run(request(BLOCK_ONLY, new TargetContext.EntityHint(entityId)), null).outcome());
        assertTrue(pipeline.run(request(BLOCK_ONLY,
                new TargetContext.BlockHint(new BlockPos(0, 64, 0))), null).isSuccess());
    }

    @Test
    void unknownAbilityResolvesToUnavailableBeforeTargetGate() {
        var result = pipeline.run(request(UNKNOWN, new TargetContext.Self()), null);
        assertEquals(ActivationOutcome.REFUSED_UNAVAILABLE, result.outcome());
    }
}
