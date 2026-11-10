package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The named mastery handler registries (REQ-CORE-12): the shared
 * declare/get/has/remove contract with loud ownership, per axis for
 * effect handlers (the same name may exist on two axes - the axis is
 * part of the key).
 */
class MasteryHandlersTest {

    @AfterEach
    void isolate() {
        MasteryGainHandlers.remove("test:gain");
        MasteryEffectHandlers.remove(MasteryAxis.COST, "test:effect");
        MasteryEffectHandlers.remove(MasteryAxis.POWER, "test:effect");
    }

    @Test
    void gainHandlerGuardsAndIsolation() {
        assertThrows(IllegalArgumentException.class, () -> MasteryGainHandlers.declare(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> MasteryGainHandlers.declare("  ", (p, s, t) -> 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> MasteryGainHandlers.declare("test:gain", null));
        MasteryGainHandlers.declare("test:gain", (p, s, t) -> 1.0);
        assertThrows(IllegalStateException.class,
                () -> MasteryGainHandlers.declare("test:gain", (p, s, t) -> 2.0),
                "two owners for one name fails loudly, never overwrites");
        assertTrue(MasteryGainHandlers.has("test:gain"));
        assertTrue(MasteryGainHandlers.get("test:gain").isPresent());
        assertFalse(MasteryGainHandlers.has(null));
        assertFalse(MasteryGainHandlers.get(null).isPresent());
        assertTrue(MasteryGainHandlers.remove("test:gain"));
        assertFalse(MasteryGainHandlers.has("test:gain"));
    }

    @Test
    void effectHandlersAreKeyedByAxisAndName() {
        assertThrows(IllegalArgumentException.class,
                () -> MasteryEffectHandlers.declare(null, "test:effect", (p, i, v) -> 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> MasteryEffectHandlers.declare(MasteryAxis.COST, "  ", (p, i, v) -> 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> MasteryEffectHandlers.declare(MasteryAxis.COST, "test:effect", null));

        MasteryEffectHandlers.declare(MasteryAxis.COST, "test:effect", (p, i, v) -> 0.5);
        assertThrows(IllegalStateException.class,
                () -> MasteryEffectHandlers.declare(MasteryAxis.COST, "test:effect",
                        (p, i, v) -> 0.9),
                "same axis and name - two owners claim one handler");
        // the axis is part of the key: the same name on another axis is free
        MasteryEffectHandlers.declare(MasteryAxis.POWER, "test:effect", (p, i, v) -> 1.5);
        assertTrue(MasteryEffectHandlers.has(MasteryAxis.COST, "test:effect"));
        assertTrue(MasteryEffectHandlers.has(MasteryAxis.POWER, "test:effect"));
        assertEquals(0.5, MasteryEffectHandlers.get(MasteryAxis.COST, "test:effect")
                .orElseThrow().multiplier(null, null, null));
        assertEquals(1.5, MasteryEffectHandlers.get(MasteryAxis.POWER, "test:effect")
                .orElseThrow().multiplier(null, null, null));

        assertFalse(MasteryEffectHandlers.get(MasteryAxis.DURATION, "test:effect").isPresent());
        assertFalse(MasteryEffectHandlers.get(null, "test:effect").isPresent());
        assertTrue(MasteryEffectHandlers.remove(MasteryAxis.COST, "test:effect"));
        assertFalse(MasteryEffectHandlers.has(MasteryAxis.COST, "test:effect"));
    }
}
