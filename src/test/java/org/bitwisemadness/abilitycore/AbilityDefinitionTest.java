package org.bitwisemadness.abilitycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.junit.jupiter.api.Test;

class AbilityDefinitionTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("tensura", "test_ability");
    private static final ResourceLocation CATEGORY = ResourceLocation.fromNamespaceAndPath("tensura", "magic");

    private AbilityDefinition sample() {
        return new AbilityDefinition(
                "tensura.skill.test",
                ResourceLocation.fromNamespaceAndPath("tensura", "textures/skill/common/test.png"),
                CATEGORY,
                ActivationType.CHARGED,
                List.of(new ResourceCost("tensura:magicule", 25.0)),
                ConsumptionStrategies.ALL,
                AmountResolvers.STATIC,
                Set.of("melee", "sword"),
                Map.of("style", "sword"));
    }

    @Test
    void codecRoundTripsAllFields() {
        var encoded = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE, sample()).getOrThrow();
        var decoded = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(sample(), decoded);
    }

    @Test
    void codecDropsOptionalFieldsToDefaults() {
        var encoded = com.google.gson.JsonParser.parseString(
                "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"instant\"}");
        var decoded = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertNull(decoded.iconId());
        assertTrue(decoded.costs().isEmpty(), "absent costs = no declared cost (never null)");
        assertEquals(ConsumptionStrategies.ALL, decoded.consumptionStrategy(),
                "absent consumption_strategy defaults to all - existing JSON stays valid");
        assertEquals(AmountResolvers.STATIC, decoded.amountResolver(),
                "absent amount_resolver defaults to static - existing JSON stays valid");
        assertTrue(decoded.tags().isEmpty());
        assertTrue(decoded.data().isEmpty());
        assertTrue(decoded.modes().isEmpty(),
                "absent modes = no declared modes - existing JSON stays valid");
    }

    @Test
    void codecAcceptsAnExplicitConsumptionStrategy() {
        var encoded = com.google.gson.JsonParser.parseString(
                "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"instant\","
                        + "\"consumption_strategy\":\"in_order\"}");
        var decoded = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(ConsumptionStrategies.IN_ORDER, decoded.consumptionStrategy());
    }

    @Test
    void unknownConsumptionStrategyFailsLoudlyAtRegistration() {
        // registration-time gate: strategies declare at construction, definitions
        // register at RegisterEvent - a name nobody declared is a wiring bug
        var def = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                "definitely_not_registered", AmountResolvers.STATIC, Set.of(), Map.of());
        var failure = assertThrows(IllegalStateException.class, () -> def.validateOrThrow(ID));
        assertTrue(failure.getMessage().contains("definitely_not_registered"),
                "the failure names the unknown strategy: " + failure.getMessage());
        // and a known one passes (sample() uses ConsumptionStrategies.ALL)
        sample().validateOrThrow(ID);
    }

    @Test
    void unknownAmountResolverFailsLoudlyAtRegistration() {
        // issue #103: same timing contract as the strategy check - resolvers
        // declare at construction, definitions register at RegisterEvent
        var def = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                ConsumptionStrategies.ALL, "definitely_not_registered", Set.of(), Map.of());
        var failure = assertThrows(IllegalStateException.class, () -> def.validateOrThrow(ID));
        assertTrue(failure.getMessage().contains("definitely_not_registered"),
                "the failure names the unknown resolver: " + failure.getMessage());
        // and the built-in static passes (sample() uses AmountResolvers.STATIC)
        sample().validateOrThrow(ID);
    }

    @Test
    void codecRoundTripsAnExplicitAmountResolver() {
        // issue #103: the wire key amount_resolver survives encode -> decode
        var encoded = com.google.gson.JsonParser.parseString(
                "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"instant\","
                        + "\"amount_resolver\":\"static\"}");
        var decoded = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(AmountResolvers.STATIC, decoded.amountResolver());
        var full = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE,
                new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, "test:wire_probe", Set.of(), Map.of()))
                .getOrThrow();
        assertTrue(full.toString().contains("\"amount_resolver\""),
                "a non-default resolver names its wire key: " + full);
        // the default never reaches the wire - absent decodes to static, so
        // pre-#103 JSON and re-encoded definitions stay byte-identical
        var withDefault = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE, sample()).getOrThrow();
        assertTrue(!withDefault.toString().contains("\"amount_resolver\""),
                "the default is omitted from the wire: " + withDefault);
        assertEquals(sample(), AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, withDefault).getOrThrow());
    }

    @Test
    void codecRoundTripsMultiEntryCosts() {
        // issue #89: N entries survive encode -> decode as a list
        var multi = new AbilityDefinition(
                "tensura.skill.test",
                ResourceLocation.fromNamespaceAndPath("tensura", "textures/skill/common/test.png"),
                CATEGORY,
                ActivationType.CHARGED,
                List.of(new ResourceCost("tensura:magicule", 25.0),
                        new ResourceCost("tensura:aura", 5.0)),
                ConsumptionStrategies.ALL,
                AmountResolvers.STATIC,
                Set.of("melee"),
                Map.of());
        var encoded = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE, multi).getOrThrow();
        assertEquals(multi, AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(encoded.toString().contains("\"costs\""), "the wire key is the costs array");
        assertTrue(!encoded.toString().contains("\"cost\""), "the legacy key never appears on the wire");
    }

    @Test
    void codecRejectsTheLegacySingleCostKey() {
        // issue #89: DFU ignores unknown keys, so without the explicit gate a
        // datapack's old cost JSON would decode to "no cost" - fail-silent
        var legacy = com.google.gson.JsonParser.parseString(
                "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"instant\","
                        + "\"cost\":{\"resource\":\"tensura:aura\",\"amount\":5.0}}");
        var failure = assertThrows(RuntimeException.class,
                () -> AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow());
        assertTrue(failure.getMessage() != null && failure.getMessage().contains("costs"),
                "the error names the replacement key: " + failure.getMessage());
    }

    @Test
    void codecAcceptsTagSet() {
        var encoded = com.google.gson.JsonParser.parseString(
                "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"instant\","
                        + "\"tags\":[\"unarmed\",\"passive\"]}");
        var decoded = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(Set.of("unarmed", "passive"), decoded.tags());
    }

    @Test
    void codecRoundTripsModes() {
        // issue #111: the wire key modes survives encode -> decode; the
        // empty default never reaches the wire (pre-#111 JSON stays valid)
        var withModes = com.google.gson.JsonParser.parseString(
                "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"instant\","
                        + "\"modes\":[\"single\",\"area\"]}");
        var decoded = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, withModes).getOrThrow();
        assertEquals(List.of("single", "area"), decoded.modes());
        var full = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE,
                new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(),
                        List.of("single", "area")))
                .getOrThrow();
        assertTrue(full.toString().contains("\"modes\""),
                "a declared cycle names its wire key: " + full);
        var empty = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE, sample()).getOrThrow();
        assertTrue(!empty.toString().contains("\"modes\""),
                "the empty default is omitted from the wire: " + empty);
        assertEquals(sample(), AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow());
    }

    @Test
    void modeCycleFollowsTheDeclaredOrder() {
        // issue #111: first = default; null/unknown current restarts there;
        // the cycle wraps; abilities with no declared modes never cycle
        var def = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(),
                List.of("single", "area", "beam"));
        assertEquals("single", def.defaultMode());
        assertEquals("single", def.nextMode(null), "no stored mode starts at the default");
        assertEquals("area", def.nextMode("single"));
        assertEquals("beam", def.nextMode("area"));
        assertEquals("single", def.nextMode("beam"), "the cycle wraps to the first");
        assertEquals("single", def.nextMode("stale_mode"),
                "an unknown stored value restarts, never crashes");
        var none = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of());
        assertNull(none.defaultMode());
        assertNull(none.nextMode("anything"), "no declared modes = nothing to cycle");
        // the retained pre-#111 constructor (the non-breaking contract) declares none
        assertNull(sample().defaultMode());
        assertNull(sample().nextMode(null));
    }

    @Test
    void codecRejectsMalformedInput() {
        // missing category
        assertThrows(Exception.class, () -> AbilityDefinition.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(
                        "{\"display_name\":\"a.b\",\"activation_type\":\"instant\"}")).getOrThrow());
        // unknown activation type (strict datapack path)
        assertThrows(Exception.class, () -> AbilityDefinition.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(
                        "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"sideways\"}"))
                .getOrThrow());
    }

    @Test
    void constructionGuardsRejectMalformedDefinitions() {
        // compact-constructor guards fire before any value can enter the registry
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityDefinition("  ", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of()));
        assertThrows(NullPointerException.class,
                () -> new AbilityDefinition("a.b", null, null, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of()));
        assertThrows(NullPointerException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, null,
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(" "), Map.of()));
        assertThrows(NullPointerException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        null, AmountResolvers.STATIC, Set.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        " ", AmountResolvers.STATIC, Set.of(), Map.of()));
        assertThrows(NullPointerException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, null, Set.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, " ", Set.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceCost("tensura:aura", -1.0));
        assertThrows(IllegalArgumentException.class, () -> new ResourceCost("", 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ResourceCost("tensura:aura", Double.NaN));
        // issue #111: the declared mode cycle is validated at construction
        assertThrows(NullPointerException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(),
                        List.of("single", " ")));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(),
                        List.of("single", "single")),
                "a duplicate makes the declared cycle ambiguous");
    }

    @Test
    void validateAcceptsAWellFormedDefinition() {
        // validateOrThrow is the registry onAdd gate; valid constructions pass it
        sample().validateOrThrow(ID);
        new AbilityDefinition("a.b", null, CATEGORY, ActivationType.UNKNOWN, List.of(),
                ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of())
                .validateOrThrow(ID); // UNKNOWN is permitted in data (refused at activation, not at registration)
    }

    @Test
    void definitionsAreImmutableValueObjects() {
        var def = sample();
        assertThrows(UnsupportedOperationException.class, () -> def.data().put("k", "v"));
        assertThrows(UnsupportedOperationException.class, () -> def.tags().add("x"));
        assertThrows(UnsupportedOperationException.class,
                () -> def.costs().add(new ResourceCost("tensura:aura", 1.0)));
        var withModes = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT,
                List.of(), ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(),
                new java.util.ArrayList<>(List.of("single")));
        assertThrows(UnsupportedOperationException.class, () -> withModes.modes().add("area"));
        assertEquals(def, sample());
        assertEquals(def.hashCode(), sample().hashCode());
    }
}
