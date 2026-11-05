package org.bitwisemadness.abilitycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
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
        return new AbilityDefinition(displayKey, null, category, ActivationType.INSTANT, List.of(),
                ConsumptionStrategies.ALL, tags, Map.of());
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
    void readdedProviderRestoresTheSameSavedReference() {
        // AC2 round-trip: a saved id survives its provider's absence and
        // resolves again - value-equal - when the provider returns.
        AbilityDefinition saved = AbilityDiscovery.get(SKILL).orElseThrow();

        // provider removed: its ids go absent, the other provider's remain
        var withoutProvider = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(withoutProvider, BATTLEWILL, def("tensura.will.a",
                ResourceLocation.fromNamespaceAndPath("tensura", "battlewill"), Set.of("active", "unarmed")));
        AbilityRegistries.installRegistryForTests(withoutProvider);
        assertEquals(PresenceState.INERT_MISSING, AbilityDiscovery.presence(SKILL));
        assertFalse(AbilityDiscovery.get(SKILL).isPresent(), "a removed provider's ids are absent");

        // provider re-added: a fresh registry carrying an equal definition
        var withProvider = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(withProvider, SKILL, def("tensura.skill.a",
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"), Set.of("active")));
        Registry.register(withProvider, BATTLEWILL, def("tensura.will.a",
                ResourceLocation.fromNamespaceAndPath("tensura", "battlewill"), Set.of("active", "unarmed")));
        AbilityRegistries.installRegistryForTests(withProvider);

        assertEquals(PresenceState.AVAILABLE, AbilityDiscovery.presence(SKILL));
        assertEquals(saved, AbilityDiscovery.get(SKILL).orElseThrow(),
                "the same saved reference resolves again, value-equal");
    }

    @Test
    void entriesCarryIdsEvenForValueEqualDefinitions() {
        // core#67: two abilities sharing display metadata must not collapse -
        // a reverse lookup keyed on the definition alone would map both to
        // one id and a consumer would then send the wrong ability.
        ResourceLocation twin = ResourceLocation.fromNamespaceAndPath("tensura", "skill_a_twin");
        Registry.register(AbilityRegistries.registry(), twin,
                def("tensura.skill.a", ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                        Set.of("active")));

        Map<ResourceLocation, AbilityDefinition> entries = AbilityDiscovery.entries();

        assertEquals(3, entries.size());
        assertEquals(entries.get(SKILL), entries.get(twin),
                "the twins are value-equal but separately keyed");
        assertEquals("tensura.skill.a", entries.get(twin).displayNameKey());
        assertThrows(UnsupportedOperationException.class,
                () -> entries.put(ABSENT, entries.get(SKILL)), "the map is immutable");
    }

    @Test
    void entriesMirrorAllAndEmptyPreReady() {
        Map<ResourceLocation, AbilityDefinition> entries = AbilityDiscovery.entries();

        assertEquals(AbilityDiscovery.all().size(), entries.size());
        assertTrue(entries.containsKey(SKILL));
        assertTrue(entries.containsKey(BATTLEWILL));
        assertEquals(AbilityDiscovery.get(SKILL).orElseThrow(), entries.get(SKILL));

        AbilityRegistries.installRegistryForTests(null);
        assertTrue(AbilityDiscovery.entries().isEmpty(), "empty pre-ready, like all()");
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
