package org.bitwisemadness.tensuraabilitycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationType;
import org.bitwisemadness.tensuraabilitycore.api.ResourceCost;
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
                new ResourceCost("tensura:magicule", 25.0),
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
        assertNull(decoded.cost());
        assertTrue(decoded.tags().isEmpty());
        assertTrue(decoded.data().isEmpty());
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
                () -> new AbilityDefinition("  ", null, CATEGORY, ActivationType.INSTANT, null, Set.of(), Map.of()));
        assertThrows(NullPointerException.class,
                () -> new AbilityDefinition("a.b", null, null, ActivationType.INSTANT, null, Set.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityDefinition("a.b", null, CATEGORY, ActivationType.INSTANT, null, Set.of(" "), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceCost("tensura:aura", -1.0));
        assertThrows(IllegalArgumentException.class, () -> new ResourceCost("", 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ResourceCost("tensura:aura", Double.NaN));
    }

    @Test
    void validateAcceptsAWellFormedDefinition() {
        // validateOrThrow is the registry onAdd gate; valid constructions pass it
        sample().validateOrThrow(ID);
        new AbilityDefinition("a.b", null, CATEGORY, ActivationType.UNKNOWN, null, Set.of(), Map.of())
                .validateOrThrow(ID); // UNKNOWN is permitted in data (refused at activation, not at registration)
    }

    @Test
    void definitionsAreImmutableValueObjects() {
        var def = sample();
        assertThrows(UnsupportedOperationException.class, () -> def.data().put("k", "v"));
        assertThrows(UnsupportedOperationException.class, () -> def.tags().add("x"));
        assertEquals(def, sample());
        assertEquals(def.hashCode(), sample().hashCode());
    }
}
