package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The amount-resolver registry (REQ-CORE-04 p4, issue #103): the built-in
 * {@code static} identity, loud ownership on {@code declare}, and the
 * null-safe existence queries that registration-time validation and test
 * isolation rest on.
 */
class AmountResolversTest {

    private static final ActivationRequest REQUEST = ActivationRequest.of(
            ResourceLocation.fromNamespaceAndPath("tensura", "test_ability"),
            ActivationSource.DIRECT_SLOT);

    @AfterEach
    void isolate() {
        AmountResolvers.remove("test:custom");
    }

    private static List<ResourceCost> declared() {
        return List.of(new ResourceCost("tensura:aura", 10.0));
    }

    @Test
    void staticIsPreRegisteredAndIsTheIdentity() {
        assertTrue(AmountResolvers.has(AmountResolvers.STATIC), "the default ships with core");
        var entry = declared();
        var priced = AmountResolvers.get(AmountResolvers.STATIC).orElseThrow()
                .resolve(entry, REQUEST, null, null);
        assertSame(entry, priced, "static passes the declared list through untouched");
    }

    @Test
    void declareEnforcesLoudOwnership() {
        assertThrows(IllegalArgumentException.class,
                () -> AmountResolvers.declare(null, (declared, request, player, target) -> declared));
        assertThrows(IllegalArgumentException.class,
                () -> AmountResolvers.declare("  ", (declared, request, player, target) -> declared));
        assertThrows(IllegalArgumentException.class,
                () -> AmountResolvers.declare("test:custom", null));

        AmountResolvers.declare("test:custom", (declared, request, player, target) -> declared);
        assertTrue(AmountResolvers.has("test:custom"));
        assertThrows(IllegalStateException.class,
                () -> AmountResolvers.declare("test:custom", (declared, request, player, target) -> declared),
                "two owners for one name must fail loudly");
        assertTrue(AmountResolvers.remove("test:custom"), "test isolation");
        assertFalse(AmountResolvers.has("test:custom"));
    }

    @Test
    void existenceQueriesAreNullSafe() {
        assertFalse(AmountResolvers.has(null));
        assertTrue(AmountResolvers.get(null).isEmpty());
        assertFalse(AmountResolvers.remove(null));
        assertFalse(AmountResolvers.has("test:absent"));
        assertTrue(AmountResolvers.get("test:absent").isEmpty());
        assertTrue(AmountResolvers.get(AmountResolvers.STATIC).isPresent());
    }
}
