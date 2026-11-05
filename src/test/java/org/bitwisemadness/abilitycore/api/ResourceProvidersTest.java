package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
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

    @Test
    void absentRequiredModSkipsBothHalvesWithoutThrowing() {
        // issue #92 AC1: absence is expected, not misuse - nothing registers,
        // no pairing IAE on the consumer half, and the debug line names the id
        try (LogCapture logs = LogCapture.forLogger(ResourceProviders.class, Level.DEBUG)) {
            ResourceProviders.declare(POOL, sufficient(), false);
            assertFalse(ResourceProviders.has(POOL), "gated provider must not register");
            assertFalse(ResourceProviders.get(POOL).isPresent());
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains(POOL)),
                    "the skip is visible in the debug log: " + logs.messages());
        }
        try (LogCapture logs = LogCapture.forLogger(ResourceConsumers.class, Level.DEBUG)) {
            ResourceConsumers.declare(POOL, noop(), false);
            assertFalse(ResourceConsumers.has(POOL), "gated consumer must not register");
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains(POOL)),
                    "the skip is visible in the debug log: " + logs.messages());
        }
        // the auto-disable chain downstream: neither half exists -> a cost
        // naming this resource fails closed with no_provider (ResourceChargeStep)
        assertFalse(ResourceProviders.has(POOL) && ResourceConsumers.has(POOL),
                "absent pair = downstream refusal, no crash");
    }

    @Test
    void presentRequiredModRegistersThePairNormally() {
        // issue #92 AC2: resolved-present behaves exactly like the ungated path
        ResourceProviders.declare(POOL, sufficient(), true);
        ResourceConsumers.declare(POOL, noop(), true);
        assertTrue(ResourceProviders.has(POOL));
        assertTrue(ResourceConsumers.has(POOL));
        assertEquals(1.0, ResourceProviders.get(POOL).orElseThrow()
                .check(null, null, 1.0).amount(), "the declared provider answers");
    }

    @Test
    void misuseStaysLoudUnderTheGatedPath() {
        // absence skips silently; present-mod with a missing provider is
        // genuine misuse and still fails with the pairing IAE
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ResourceConsumers.declare(POOL, noop(), true));
        assertTrue(missing.getMessage().contains(POOL), missing.getMessage());

        ResourceProviders.declare(POOL, sufficient(), true);
        ResourceConsumers.declare(POOL, noop(), true);
        assertThrows(IllegalStateException.class, () -> ResourceConsumers.declare(POOL, noop()),
                "duplicates stay loud even when gated declarations are available");
    }

    @Test
    void nullRequiredModIdIsUngated() {
        // the built-ins' path: null short-circuits before any ModList touch,
        // so this test never boots FML either
        ResourceProviders.declare(POOL, sufficient(), (String) null);
        assertTrue(ResourceProviders.has(POOL), "null modId = ungated declare");
    }
}
