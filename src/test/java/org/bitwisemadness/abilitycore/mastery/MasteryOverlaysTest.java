package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The overlay-aware spec lookup (REQ-CORE-12): specFor merges the
 * data-map overlay over the code declaration at read, never creates a
 * spec for an undeclared id (opt-in stays single-sourced), and is live
 * through the test seam exactly like DefinitionOverlays.
 */
class MasteryOverlaysTest {

    private static final ResourceLocation DECLARED =
            ResourceLocation.fromNamespaceAndPath("tensura", "declared_probe");
    private static final ResourceLocation UNDECLARED =
            ResourceLocation.fromNamespaceAndPath("tensura", "undeclared_probe");

    @AfterEach
    void isolate() {
        MasteryOverlays.installOverlaysForTests(null);
        MasteryDeclarations.remove(DECLARED);
        MasteryDeclarations.remove(UNDECLARED);
    }

    private static MasterySpecOverlay overlay(String json) {
        return MasterySpecOverlay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
    }

    @Test
    void undeclaredIdsStayOptedOutEvenWithAnOverlay() {
        MasteryOverlays.installOverlaysForTests(
                Map.of(UNDECLARED, overlay("{ \"base_gain\": 500.0 }")));
        assertNull(MasteryOverlays.specFor(UNDECLARED),
                "an overlay never opts an id in - declaration is the single opt-in");
    }

    @Test
    void declaredWithoutOverlayReadsTheBase() {
        MasteryDeclarations.declare(DECLARED, MasterySpec.gained(10.0));
        MasterySpec spec = MasteryOverlays.specFor(DECLARED);
        assertEquals(10.0, spec.baseGain());
        assertTrue(spec.effects().isEmpty());
    }

    @Test
    void declaredWithOverlayReadsTheMerge() {
        MasteryDeclarations.declare(DECLARED, MasterySpec.gained(10.0)
                .withEffect(MasteryAxis.COST));
        MasteryOverlays.installOverlaysForTests(Map.of(DECLARED,
                overlay("{ \"base_gain\": 40.0, \"effects\": [] }")));
        MasterySpec merged = MasteryOverlays.specFor(DECLARED);
        assertEquals(40.0, merged.baseGain(), "the overlay's gain wins");
        assertTrue(merged.effects().isEmpty(), "the empty effects array clears participation");
    }

    @Test
    void nullOverlaySourceFallsBackToDeclarations() {
        MasteryDeclarations.declare(DECLARED, MasterySpec.gained(7.0));
        MasteryOverlays.installOverlaysForTests(null);
        assertEquals(7.0, MasteryOverlays.specFor(DECLARED).baseGain());
        assertNull(MasteryOverlays.specFor(UNDECLARED));
    }
}
