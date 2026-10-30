package org.bitwisemadness.abilitycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery.PresenceState;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Discovery semantics (REQ-CORE-03): presence states, provider-absent
 * handling, open category/tag enumeration, registered-vs-resolvable.
 */
class AbilityDiscoveryTest {

    private static final ResourceLocation SKILL = ResourceLocation.fromNamespaceAndPath("tensura", "skill_a");
    private static final ResourceLocation BATTLEWILL = ResourceLocation.fromNamespaceAndPath("tensura", "will_a");
    private static final ResourceLocation ABSENT = ResourceLocation.fromNamespaceAndPath("tensura", "provider_is_gone");

    private Registry<AbilityDefinition> previousRegistry;

    @BeforeEach
    void installFixtureRegistry() {
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, SKILL, def("tensura.skill.a",
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"), Set.of("active")));
        Registry.register(fixture, BATTLEWILL, def("tensura.will.a",
                ResourceLocation.fromNamespaceAndPath("tensura", "battlewill"), Set.of("active", "unarmed")));
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void restoreRegistry() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
    }

    private static AbilityDefinition def(String displayKey, ResourceLocation category, Set<String> tags) {
        return new AbilityDefinition(displayKey, null, category, ActivationType.INSTANT, null, tags, Map.of());
    }

    @Test
    void presenceReflectsRegistrationTruth() {
        assertTrue(AbilityDiscovery.isReady());
        assertEquals(PresenceState.AVAILABLE, AbilityDiscovery.presence(SKILL));
        assertEquals(PresenceState.INERT_MISSING, AbilityDiscovery.presence(ABSENT));
        assertFalse(AbilityDiscovery.isRegistered(ABSENT));
        assertTrue(AbilityDiscovery.get(SKILL).isPresent());
        assertFalse(AbilityDiscovery.get(ABSENT).isPresent());
        assertFalse(AbilityDiscovery.getIfAvailable(null).isPresent());
    }

    @Test
    void notReadyWindowReportsPlaceholderState() {
        AbilityRegistries.installRegistryForTests(null);
        assertFalse(AbilityDiscovery.isReady());
        assertEquals(PresenceState.NOT_READY, AbilityDiscovery.presence(SKILL));
        assertTrue(AbilityDiscovery.all().isEmpty());
        assertFalse(AbilityDiscovery.isRegistered(SKILL));
    }

    @Test
    void openCategoryAndTagEnumerationCoversKnownKinds() {
        assertEquals(1, AbilityDiscovery.byCategory(ResourceLocation.fromNamespaceAndPath("tensura", "skill")).size());
        assertEquals(1, AbilityDiscovery.byCategory(ResourceLocation.fromNamespaceAndPath("tensura", "battlewill")).size());
        assertEquals(0, AbilityDiscovery.byCategory(ResourceLocation.fromNamespaceAndPath("tensura", "magic")).size());
        // the four-kind fixture from the issue AC: skill/magic/spell/battlewill categories
        // never require a core change - categories are plain ResourceLocations (here: 2 kinds
        // registered; the other kinds are added by providers with no core edit).
        assertEquals(2, AbilityDiscovery.byTag("active").size());
        assertEquals(1, AbilityDiscovery.byTag("unarmed").size());
        assertEquals(0, AbilityDiscovery.byTag("passive").size());
        assertTrue(AbilityDiscovery.byTag(null).isEmpty());
        assertTrue(AbilityDiscovery.byTag("  ").isEmpty());
        assertEquals(2, AbilityDiscovery.all().size());
    }

    @Test
    void registeredVersusResolvableTracksHandlerPresence() {
        // registered but no handler -> registered, not resolvable (NO_ADAPTER later)
        assertTrue(AbilityDiscovery.isRegistered(SKILL));
        assertFalse(AbilityDiscovery.isResolvable(SKILL));
        // with a handler it becomes resolvable
        AbilityCore.HANDLERS.register(SKILL, (req, player) ->
                org.bitwisemadness.abilitycore.api.ActivationResult.ACTED);
        try {
            assertTrue(AbilityDiscovery.isResolvable(SKILL));
            assertFalse(AbilityDiscovery.isResolvable(ABSENT));
        } finally {
            // remove the fixture handler (documented removal API: handlers are
            // per-load lifecycle objects; tests and future reload paths use this)
            AbilityCore.HANDLERS.remove(SKILL);
        }
    }
}
