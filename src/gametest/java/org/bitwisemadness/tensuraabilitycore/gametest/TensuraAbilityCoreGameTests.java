package org.bitwisemadness.tensuraabilitycore.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.tensuraabilitycore.TensuraAbilityCore;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;

/**
 * Smoke tests: the mod loads, the GameTest harness runs, and the core ability
 * registry exists in a loaded game (REQ-CORE-01 wiring evidence).
 *
 * <p>Keep the {@code expectedTests} count in {@code build.gradle} in sync with
 * the number of {@code @GameTest} methods here, or the harness fails the run.</p>
 */
@GameTestHolder(TensuraAbilityCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TensuraAbilityCoreGameTests {

    private TensuraAbilityCoreGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * The ability registry was created by NewRegistryEvent with the exported
     * key - the foundation every provider/consumer issue builds on.
     */
    @GameTest(template = "empty")
    public static void ability_registry_is_registered(GameTestHelper helper) throws GameTestAssertException {
        if (AbilityRegistries.registry() == null) {
            throw new GameTestAssertException("ability registry was not created during mod loading");
        }
        if (!AbilityRegistries.ABILITY_REGISTRY_KEY.equals(AbilityRegistries.registry().key())) {
            throw new GameTestAssertException("ability registry has unexpected key: "
                    + AbilityRegistries.registry().key());
        }
        // Registry mechanics sanity: ids resolve as resource locations and the
        // registry is enumerable (content may be empty at this stage).
        AbilityRegistries.registry().keySet().forEach(location -> {
            if (location == null) {
                throw new GameTestAssertException("registry entry with null location");
            }
        });
        helper.succeed();
    }

    /**
     * The pipeline's built-in refusal path works against the real loaded
     * registry: an unknown id answers REFUSED_UNAVAILABLE with no steps run
     * (REQ-CORE-02 integration evidence).
     */
    @GameTest(template = "empty")
    public static void pipeline_refuses_unknown_ability(GameTestHelper helper) throws GameTestAssertException {
        var pipeline = new org.bitwisemadness.tensuraabilitycore.activation.ActivationPipeline(
                new org.bitwisemadness.tensuraabilitycore.activation.ActivationHandlerRegistry());
        var request = org.bitwisemadness.tensuraabilitycore.api.ActivationRequest.of(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tensura", "definitely_not_registered"),
                org.bitwisemadness.tensuraabilitycore.api.ActivationSource.DIRECT_SLOT);
        // player is legitimately absent: no step may execute before the RESOLVE refusal
        var result = pipeline.run(request, null);
        if (result.outcome() != org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome.REFUSED_UNAVAILABLE) {
            throw new GameTestAssertException("expected REFUSED_UNAVAILABLE, got " + result.outcome());
        }
        helper.succeed();
    }
}
