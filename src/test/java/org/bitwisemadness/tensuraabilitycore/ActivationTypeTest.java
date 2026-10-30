package org.bitwisemadness.tensuraabilitycore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.bitwisemadness.tensuraabilitycore.api.ActivationType;
import org.junit.jupiter.api.Test;

class ActivationTypeTest {

    @Test
    void strictCodecAcceptsKnownValues() {
        assertEquals(ActivationType.INSTANT, strict("instant"));
        assertEquals(ActivationType.CHARGED, strict("charged"));
        assertEquals(ActivationType.CHANNELLED, strict("channelled"));
        assertEquals(ActivationType.TOGGLE, strict("toggle"));
        assertEquals(ActivationType.UNKNOWN, strict("unknown"));
    }

    @Test
    void strictCodecRejectsUnknownValues() {
        // datapack layer: invalid values produce diagnostics, never apply silently
        assertThrows(Exception.class, () -> strict("sideways"));
        assertThrows(Exception.class, () -> strict(""));
    }

    @Test
    void wireMappingIsFailClosed() {
        // runtime layer: unrecognised -> UNKNOWN, never an exception, never a guess
        assertEquals(ActivationType.UNKNOWN, ActivationType.fromWire("sideways"));
        assertEquals(ActivationType.UNKNOWN, ActivationType.fromWire(null));
        assertEquals(ActivationType.UNKNOWN, ActivationType.fromWire("  "));
        assertEquals(ActivationType.INSTANT, ActivationType.fromWire(" INSTANT "));
        assertEquals(ActivationType.CHANNELLED, ActivationType.fromWire("continuous"));
    }

    @Test
    void wireNamesRoundTrip() {
        for (ActivationType type : ActivationType.values()) {
            assertEquals(type, ActivationType.fromWire(type.wireName()));
            assertDoesNotThrow(() -> strict(type.wireName()));
        }
    }

    private static ActivationType strict(String wire) {
        return ActivationType.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(wire)).getOrThrow();
    }
}
