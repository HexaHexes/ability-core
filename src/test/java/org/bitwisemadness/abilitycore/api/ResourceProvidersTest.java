package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The provider registry and the pairing rule (REQ-CORE-04, issue #88): one
 * provider per resource id with loud failures, and a consumer that can only
 * be declared once its provider exists - validation and deduction can never
 * drift apart. The existence queries double as the conditional-selection API
 * (pseudocore#174).
 */
class ResourceProvidersTest {

    private static final String POOL = "test:providers";
    private static final String OTHER = "test:other_pool";

    @AfterEach
    void clean() {
        ResourceConsumers.remove(POOL);
        ResourceProviders.remove(POOL);
        ResourceConsumers.remove(OTHER);
        ResourceProviders.remove(OTHER);
    }

    private static ResourceProvider sufficient() {
        return (abilityId, player, amount) -> Affordability.sufficient(POOL, amount);
    }

    private static ResourceConsumer noop() {
        return (abilityId, player, snapshot) -> {
            // charge half - deliberately empty for these fixtures
        };
    }

    @Test
    void registryRulesMirrorTheConsumerSide() {
        assertFalse(ResourceProviders.has(POOL), "starts absent");
        assertFalse(ResourceProviders.get(POOL).isPresent());

        ResourceProviders.declare(POOL, sufficient());
        assertTrue(ResourceProviders.has(POOL));
        assertEquals(POOL, ResourceProviders.get(POOL).orElseThrow()
                .check(null, null, 1.0).resourceId(), "get answers the declared provider");

        assertThrows(IllegalArgumentException.class, () -> ResourceProviders.declare(null, sufficient()));
        assertThrows(IllegalArgumentException.class, () -> ResourceProviders.declare("  ", sufficient()));
        assertThrows(IllegalArgumentException.class, () -> ResourceProviders.declare(POOL, null));
        assertThrows(IllegalStateException.class, () -> ResourceProviders.declare(POOL, sufficient()),
                "duplicate provider for one currency fails loudly");

        assertTrue(ResourceProviders.remove(POOL));
        assertFalse(ResourceProviders.has(POOL));
        assertFalse(ResourceProviders.remove(POOL), "second removal is a no-op");
        assertFalse(ResourceProviders.has(null));
        assertFalse(ResourceProviders.remove(null));
    }

    @Test
    void consumerDeclareRequiresItsProviderFirst() {
        // AC2: the pairing rule - a consumer without its provider is a
        // declaration-order error naming the resource, never a silent start
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ResourceConsumers.declare(POOL, noop()));
        assertTrue(missing.getMessage().contains(POOL), missing.getMessage());
        assertTrue(missing.getMessage().contains("provider"), missing.getMessage());
        assertFalse(ResourceConsumers.has(POOL), "nothing was registered");

        ResourceProviders.declare(POOL, sufficient());
        ResourceConsumers.declare(POOL, noop());
        assertTrue(ResourceConsumers.has(POOL), "with the provider present, the pair declares");

        assertThrows(IllegalStateException.class, () -> ResourceConsumers.declare(POOL, noop()),
                "the consumer side keeps its own duplicate rule");
    }

    @Test
    void existenceQueriesSupportConditionalSelection() {
        // pseudocore#174's rule shape: ask both registries before choosing costs
        assertFalse(ResourceProviders.has("tensura:aura") && ResourceConsumers.has("tensura:aura"),
                "unregistered currencies read as absent - the fallback branch is reachable");

        ResourceProviders.declare(OTHER, (abilityId, player, amount) ->
                Affordability.sufficient(OTHER, amount));
        assertTrue(ResourceProviders.has(OTHER), "registered reads as present");
        assertFalse(ResourceConsumers.has(OTHER), "provider without consumer is still visible as such");
    }
}
