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
import org.bitwisemadness.abilitycore.api.Stat;
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
    void codecRoundTripsStats() {
        // issue #119: the wire key stats survives encode -> decode, in order;
        // the empty default never reaches the wire (existing JSON stays valid)
        var withStats = com.google.gson.JsonParser.parseString(
                "{\"display_name\":\"a.b\",\"category\":\"mod:cat\",\"activation_type\":\"instant\","
                        + "\"stats\":[{\"label\":\"mod.stat.power\",\"value\":\"200\"},"
                        + "{\"label\":\"mod.stat.range\",\"value\":\"15 m\"}]}");
        var decoded = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, withStats).getOrThrow();
        assertEquals(List.of(new Stat("mod.stat.power", "200"), new Stat("mod.stat.range", "15 m")),
                decoded.stats(), "declaration order is presentation order");
        var full = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE,
                new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, List.of(),
                        ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(),
                        List.of(new Stat("mod.stat.power", "200"))))
                .getOrThrow();
        assertTrue(full.toString().contains("\"stats\""),
                "a declared stat names its wire key: " + full);
        var empty = AbilityDefinition.CODEC.encodeStart(JsonOps.INSTANCE, sample()).getOrThrow();
        assertTrue(!empty.toString().contains("\"stats\""),
                "the empty default is omitted from the wire: " + empty);
        assertEquals(sample(), AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow());
    }

    @Test
    void statsValidateAtConstruction() {
        // issue #119: Stat owns label/value; the definition owns the list
        assertThrows(IllegalArgumentException.class, () -> new Stat(" ", "200"));
        assertThrows(IllegalArgumentException.class, () -> new Stat("mod.stat.power", " "));
        assertThrows(NullPointerException.class, () -> new Stat(null, "200"));
        assertThrows(NullPointerException.class, () -> new Stat("mod.stat.power", null));
        assertThrows(NullPointerException.class, () -> new AbilityDefinition("a.b", null, CATEGORY,
                ActivationType.INSTANT, List.of(), ConsumptionStrategies.ALL,
                AmountResolvers.STATIC, Set.of(), Map.of(), null));
        var withStats = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT,
                List.of(), ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of(),
                List.of(new Stat("mod.stat.power", "200")));
        withStats.validateOrThrow(ID); // a well-formed declaration passes the registry gate
    }

    @Test
    void theRetainedPreStatsConstructorDeclaresNoStats() {
        // the non-breaking contract (issue #119): the previous signature stays
        var legacy = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT,
                List.of(), ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(), Map.of());
        assertTrue(legacy.stats().isEmpty(), "the retained constructor means stats = List.of()");
        assertEquals(sample().stats(), legacy.stats());
        legacy.validateOrThrow(ID);
    }

    @Test
    void descriptionReadsTheFormalDataSlot() {
        // issue #119: data.description is THE description slot, one accessor
        var withDescription = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT,
                List.of(), ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(),
                Map.of("description", "Hurls a bolt of force."));
        assertEquals("Hurls a bolt of force.", withDescription.description().orElseThrow());
        assertTrue(sample().description().isEmpty(), "no declared description reads empty");
        var blankDescription = new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT,
                List.of(), ConsumptionStrategies.ALL, AmountResolvers.STATIC, Set.of(),
                Map.of("description", "  "));
        assertTrue(blankDescription.description().isEmpty(),
                "a blank slot reads as none - never an empty tooltip line");
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
        assertEquals(def, sample());
        assertEquals(def.hashCode(), sample().hashCode());
    }
}
