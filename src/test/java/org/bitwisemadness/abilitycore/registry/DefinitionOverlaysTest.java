package org.bitwisemadness.abilitycore.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery.PresenceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Datapack definition overlays (issue #12's data-map sub-part): the partial
 * overlay schema and its merge-at-lookup rules, discovery's merged view, and
 * the fail-closed guarantees - an overlay can only modify a registered id,
 * never resurrect a removed one, and never changes id/presence semantics.
 */
class DefinitionOverlaysTest {

    private static final ResourceLocation SKILL =
            ResourceLocation.fromNamespaceAndPath("tensura", "skill_a");
    private static final ResourceLocation ABSENT =
            ResourceLocation.fromNamespaceAndPath("tensura", "not_registered");
    private static final ResourceLocation SKILL_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "skill");

    private Registry<AbilityDefinition> previousRegistry;

    @BeforeEach
    void installFixtureRegistry() {
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, SKILL, base());
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void restore() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        DefinitionOverlays.installOverlaysForTests(null);
    }

    private static AbilityDefinition base() {
        return new AbilityDefinition("tensura.skill.a", null, SKILL_CATEGORY,
                ActivationType.INSTANT, List.of(), Set.of("active"), Map.of("k", "v"));
    }

    private static DefinitionOverlay overlay(String displayName, Map<String, String> data) {
        return new DefinitionOverlay(displayName, null, null, null, null, null, data);
    }

    @Test
    void overlayCodecRoundTripsFullAndPartial() {
        var full = new DefinitionOverlay("tensura.skill.b",
                ResourceLocation.fromNamespaceAndPath("tensura", "icon"),
                ResourceLocation.fromNamespaceAndPath("tensura", "magic"),
                ActivationType.INSTANT, List.of(new ResourceCost("tensura:aura", 5.0)),
                Set.of("custom"), Map.of("extra", "1"));
        var json = DefinitionOverlay.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow();
        assertEquals(full, DefinitionOverlay.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        var partial = DefinitionOverlay.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"display_name\":\"tensura.skill.c\"}"))
                .getOrThrow();
        assertEquals("tensura.skill.c", partial.displayNameKey());
        assertNull(partial.category(), "absent fields stay absent - they keep the base at merge");
        assertNull(partial.costs());
        assertNull(partial.tags());
        assertNull(partial.data());

        var empty = DefinitionOverlay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .getOrThrow();
        assertNull(empty.displayNameKey(), "an empty overlay is a legal no-op");
    }

    @Test
    void malformedOverlayJsonIsRejected() {
        assertThrows(Exception.class, () -> DefinitionOverlay.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"display_name\":\"  \"}")).getOrThrow());
        assertThrows(Exception.class, () -> DefinitionOverlay.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"category\":\"not a resource location\"}")).getOrThrow());
    }

    @Test
    void overlayCodecRejectsTheLegacySingleCostKey() {
        // issue #89: DFU ignores unknown keys - a datapack's old cost JSON
        // would silently stop applying (fail-silent), so it is rejected loudly
        var failure = assertThrows(RuntimeException.class, () -> DefinitionOverlay.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"cost\":{\"resource\":\"tensura:aura\",\"amount\":5.0}}")).getOrThrow());
        assertTrue(failure.getMessage() != null && failure.getMessage().contains("costs"),
                "the error names the replacement key: " + failure.getMessage());
    }

    @Test
    void costsReplaceWholesaleWhenPresent() {
        var baseWithCosts = new AbilityDefinition("tensura.skill.a", null, SKILL_CATEGORY,
                ActivationType.INSTANT, List.of(new ResourceCost("tensura:aura", 5.0)),
                Set.of("active"), Map.of("k", "v"));
        var replacement = List.of(new ResourceCost("tensura:health", 10.0),
                new ResourceCost("tensura:aura", 2.0));
        var merged = new DefinitionOverlay(null, null, null, null, replacement, null, null)
                .mergeInto(baseWithCosts);
        assertEquals(replacement, merged.costs(),
                "a present costs list replaces the base wholesale - no union, no per-entry merge");
        assertEquals(baseWithCosts.costs(), overlay("tensura.skill.x", Map.of()).mergeInto(baseWithCosts).costs(),
                "absent costs keep the base");
    }

    @Test
    void presentFieldsReplaceAndAbsentFieldsKeepTheBase() {
        var merged = overlay("tensura.skill.overlayed", Map.of("k2", "v2")).mergeInto(base());

        assertEquals("tensura.skill.overlayed", merged.displayNameKey());
        assertEquals(SKILL_CATEGORY, merged.category(), "absent category keeps the base");
        assertEquals(ActivationType.INSTANT, merged.activationType());
        assertEquals(Set.of("active"), merged.tags(), "absent tags keep the base");
        assertEquals(Map.of("k", "v", "k2", "v2"), merged.data(),
                "data merges per-key: base keys kept, overlay wins conflicts");
    }

    @Test
    void tagsReplaceWholesaleWhenPresent() {
        var merged = new DefinitionOverlay(null, null, null, null, null,
                Set.of("custom"), null).mergeInto(base());
        assertEquals(Set.of("custom"), merged.tags(), "present tags replace, never union");
        assertEquals("tensura.skill.a", merged.displayNameKey());
    }

    @Test
    void anEmptyOverlayIsValueEqualToTheBase() {
        var noOp = new DefinitionOverlay(null, null, null, null, null, null, null);
        assertEquals(base(), noOp.mergeInto(base()), "a no-op overlay changes nothing");
    }

    @Test
    void discoveryLookupsReturnTheMergedDefinition() {
        DefinitionOverlays.installOverlaysForTests(
                Map.of(SKILL, overlay("tensura.skill.overlayed", Map.of("k2", "v2"))));

        AbilityDefinition fromGet = AbilityDiscovery.get(SKILL).orElseThrow();
        assertEquals("tensura.skill.overlayed", fromGet.displayNameKey());
        assertEquals(Map.of("k", "v", "k2", "v2"), fromGet.data());

        assertEquals(fromGet, AbilityDiscovery.entries().get(SKILL),
                "the id-aware enumeration must show the same merged value");
        assertTrue(AbilityDiscovery.all().contains(fromGet));
        assertEquals(PresenceState.AVAILABLE, AbilityDiscovery.presence(SKILL),
                "overlays never change id or presence semantics");
    }

    @Test
    void overlaysCannotResurrectAbsentIds() {
        DefinitionOverlays.installOverlaysForTests(
                Map.of(ABSENT, overlay("tensura.skill.ghost", Map.of())));

        assertTrue(AbilityDiscovery.get(ABSENT).isEmpty(),
                "an overlay for an unregistered id must not create a definition");
        assertEquals(PresenceState.INERT_MISSING, AbilityDiscovery.presence(ABSENT));
    }

    @Test
    void theRegistryDataMapApiIsPresentInTheTestEnvironment() {
        // The production read path (registry.getData) lives on NeoForge's
        // patched Registry - pin that it exists here and answers null when no
        // data map value is attached, so a classpath regression fails loudly.
        Registry<AbilityDefinition> registry = AbilityRegistries.registry();
        assertNull(registry.getData(DefinitionOverlays.TYPE,
                ResourceKey.create(AbilityRegistries.ABILITY_REGISTRY_KEY, SKILL)));
    }
}
