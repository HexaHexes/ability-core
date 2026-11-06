package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Amount resolver registry (issue #103): the built-in {@code static} identity
 * belongs to core, declarations are loud, and existence queries support
 * registration-time name validation - same contract as strategies/providers.
 */
class AmountResolversTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "resolver_probe");
    private static final ResourceCost ENTRY = new ResourceCost("test:a", 10.0);
    private static final ActivationRequest REQUEST =
            ActivationRequest.of(ABILITY, ActivationSource.DIRECT_SLOT);

    @AfterEach
    void reset() {
        AmountResolvers.remove("test:custom");
        AmountResolvers.remove("test:observing");
    }

    @Test
    void staticBuiltinIsPreRegisteredAndIdentity() {
        assertTrue(AmountResolvers.has(AmountResolvers.STATIC));
        var priced = AmountResolvers.get(AmountResolvers.STATIC).orElseThrow()
                .resolve(List.of(ENTRY), REQUEST, null, null);
        assertEquals(List.of(ENTRY), priced, "static passes the declared list through unchanged");
    }

    @Test
    void declareEnforcesLoudOwnership() {
        assertThrows(IllegalArgumentException.class, () -> AmountResolvers.declare(null, (d, r, p, t) -> d));
        assertThrows(IllegalArgumentException.class, () -> AmountResolvers.declare("  ", (d, r, p, t) -> d));
        assertThrows(IllegalArgumentException.class,
                () -> AmountResolvers.declare("test:custom", null));
        AmountResolvers.declare("test:custom", (d, r, p, t) -> d);
        assertThrows(IllegalStateException.class,
                () -> AmountResolvers.declare("test:custom", (d, r, p, t) -> d),
                "two owners for one resolver name fails loudly, never overwrites");
        assertTrue(AmountResolvers.remove("test:custom"), "test isolation");
        assertFalse(AmountResolvers.has("test:custom"));
        assertFalse(AmountResolvers.has(null));
        assertFalse(AmountResolvers.get("definitely_absent").isPresent());
    }

    @Test
    void resolverReceivesTheDeclaredListTargetAndPlayer() {
        var seen = new java.util.ArrayList<String>();
        AmountResolvers.declare("test:observing", (declared, request, player, target) -> {
            seen.add(declared + "|" + request.abilityId() + "|" + player + "|" + target);
            return declared;
        });
        AmountResolvers.get("test:observing").orElseThrow()
                .resolve(List.of(ENTRY), REQUEST, null, "stub-target");
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).contains(ABILITY.toString()), seen.get(0));
        assertTrue(seen.get(0).contains("stub-target"), seen.get(0));
        assertTrue(seen.get(0).contains("null"), "null player passed through: " + seen.get(0));
    }
}
