package org.bitwisemadness.abilitycore.interop.leylines;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The Ley Lines bridge's declaration surface (issue #96): the adopter supplies
 * the currency id (agnostic machinery - nothing here enumerates currencies),
 * absence skips loudly-in-debug before any upstream class links, presence
 * registers the pair. Live quote/spend needs a real world with the mod
 * installed - that stays a manual dev-run check.
 */
class LeyLinesResourcesTest {

    private static final ResourceLocation CURRENCY = ResourceLocation.parse("technomancy:aether");
    private static final String RESOURCE_ID = "technomancy:aether";

    @AfterEach
    void clean() {
        ResourceConsumers.remove(RESOURCE_ID);
        ResourceProviders.remove(RESOURCE_ID);
    }

    @Test
    void nullCurrencyIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LeyLinesResources.declare(null, false));
        assertThrows(IllegalArgumentException.class, () -> LeyLinesResources.declare(null, true));
    }

    @Test
    void absentModSkipsWithDebugAndRegistersNothing() {
        try (LogCapture logs = LogCapture.forLogger(LeyLinesResources.class, Level.DEBUG)) {
            assertFalse(LeyLinesResources.declare(CURRENCY, false),
                    "absence answers 'not declared'");
            assertFalse(ResourceProviders.has(RESOURCE_ID), "no provider registered");
            assertFalse(ResourceConsumers.has(RESOURCE_ID), "no consumer registered");
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains(RESOURCE_ID)),
                    "the skip names the currency: " + logs.messages());
        }
        // the downstream auto-disable chain reads exactly this shape (#92)
        assertFalse(ResourceProviders.has(RESOURCE_ID) && ResourceConsumers.has(RESOURCE_ID),
                "absent pair = a cost naming it fails closed with no_provider");
    }

    @Test
    void presentModRegistersTheAdoptersPairExactlyOnce() {
        assertTrue(LeyLinesResources.declare(CURRENCY, true), "presence declares the pair");
        assertTrue(ResourceProviders.has(RESOURCE_ID), "resource id IS the currency id string");
        assertTrue(ResourceConsumers.has(RESOURCE_ID));
        // check/charge are never invoked here: quoting needs a live ServerLevel
        assertThrows(IllegalStateException.class, () -> LeyLinesResources.declare(CURRENCY, true),
                "duplicate declaration fails loudly (pairing ownership)");
    }
}
