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
        if (AbilityRegistries.ABILITY_REGISTRY == null) {
            throw new GameTestAssertException("ability registry was not created during mod loading");
        }
        if (!AbilityRegistries.ABILITY_REGISTRY_KEY.equals(AbilityRegistries.ABILITY_REGISTRY.key())) {
            throw new GameTestAssertException("ability registry has unexpected key: "
                    + AbilityRegistries.ABILITY_REGISTRY.key());
        }
        // Registry mechanics sanity: ids resolve as resource locations and the
        // registry is enumerable (content may be empty at this stage).
        AbilityRegistries.ABILITY_REGISTRY.keySet().forEach(location -> {
            if (location == null) {
                throw new GameTestAssertException("registry entry with null location");
            }
        });
        helper.succeed();
    }
}
