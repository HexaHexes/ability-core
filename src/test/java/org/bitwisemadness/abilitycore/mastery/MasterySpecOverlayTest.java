package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.junit.jupiter.api.Test;

/**
 * The datapack spec-overlay schema and merge (REQ-CORE-12): decode
 * rejects unknown names and bad shares at load (fail to base), and
 * mergeInto is present-replaces/absent-keeps with lists replacing
 * wholesale - the datapack-overrides.md convention.
 */
class MasterySpecOverlayTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "overlay_probe");

    private static MasterySpecOverlay decode(String json) {
        return MasterySpecOverlay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
    }

    /** True when the schema rejects the JSON - by result or by ctor throw. */
    private static boolean rejects(String json) {
        try {
            return MasterySpecOverlay.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isEmpty();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static MasterySpec base() {
        return MasterySpec.gained(10.0)
                .withShare(MasteryTarget.category(
                        ResourceLocation.fromNamespaceAndPath("tensura", "base_cat")), 0.25)
                .withEffect(MasteryAxis.COST);
    }

    @Test
    void fullOverlayDecodes() {
        var overlay = decode("""
                {
                  "base_gain": 25.0,
                  "gain_handler": "mymod:scale",
                  "triggers": ["ACTIVATION", "HIT"],
                  "shares": [
                    {"kind": "CATEGORY", "id": "tensura:fire", "share": 0.5}
                  ],
                  "effects": [
                    {"axis": "COST", "handler": "mymod:discount"},
                    {"axis": "POWER"}
                  ]
                }""");
        assertEquals(25.0, overlay.baseGain());
        assertEquals("mymod:scale", overlay.gainHandler());
        assertEquals(2, overlay.triggers().size());
        assertEquals(MasteryTrigger.HIT, overlay.triggers().get(1));
        assertEquals(1, overlay.shares().size());
        assertEquals(MasteryTarget.Kind.CATEGORY, overlay.shares().get(0).kind());
        assertNull(overlay.effects().get(1).handler(),
                "a missing handler means the default curve");
    }

    @Test
    void partialOverlayDecodesToNulls() {
        var overlay = decode("{ \"base_gain\": 99.0 }");
        assertEquals(99.0, overlay.baseGain());
        assertNull(overlay.gainHandler());
        assertNull(overlay.triggers());
        assertNull(overlay.shares());
        assertNull(overlay.effects());
    }

    @Test
    void schemaRejectsBadShapesAtDecode() {
        assertTrue(rejects("{ \"triggers\": [\"SOMETIMES\"] }"), "unknown trigger");
        assertTrue(rejects("{ \"shares\": [{\"kind\": \"WORLD\", \"id\": \"tensura:x\", "
                + "\"share\": 0.5}] }"), "unknown target kind");
        assertTrue(rejects("{ \"effects\": [{\"axis\": \"LEVITATE\"}] }"), "unknown axis");
        assertTrue(rejects("{ \"shares\": [{\"kind\": \"CATEGORY\", \"id\": \"tensura:x\", "
                + "\"share\": 0.0}] }"), "zero share");
        assertTrue(rejects("{ \"shares\": [{\"kind\": \"CATEGORY\", \"id\": \"tensura:x\", "
                + "\"share\": 1.5}] }"), "share above 1");
        assertTrue(rejects("{ \"shares\": [{\"kind\": \"CATEGORY\", \"id\": \"tensura:x\", "
                + "\"share\": 0.6}, {\"kind\": \"ABILITY\", \"id\": \"tensura:y\", "
                + "\"share\": 0.6}] }"), "share sum above 1");
        assertTrue(rejects("{ \"effects\": [{\"axis\": \"COST\"}, {\"axis\": \"COST\"}] }"),
                "duplicate axis");
        assertTrue(rejects("{ \"base_gain\": -1.0 }"), "negative base gain");
        assertTrue(rejects("{ \"gain_handler\": \"  \" }"), "blank handler");
        // an unrelated but well-formed object is a legal no-op overlay
        // (DFU ignores unknown keys - the same rule definition overlays live by)
        var noop = decode("{ \"levels\": [1] }");
        assertNull(noop.baseGain());
        assertNull(noop.shares());
        assertNull(noop.effects());
    }

    @Test
    void absentFieldsKeepTheBasePresentReplace() {
        var base = base();
        var merged = decode("{ \"base_gain\": 50.0 }").mergeInto(base);
        assertEquals(50.0, merged.baseGain(), "present replaces");
        assertEquals(base.triggers(), merged.triggers(), "absent keeps");
        assertEquals(base.shares(), merged.shares(), "absent keeps");
        assertEquals(base.effects(), merged.effects(), "absent keeps");
        assertEquals(base.gainHandler(), merged.gainHandler());

        // lists replace wholesale - the overlay's statement is complete
        var replaced = decode("""
                { "triggers": ["KILL"],
                  "shares": [{"kind": "SUBCATEGORY", "id": "tensura:fireball", "share": 1.0}],
                  "effects": [] }""").mergeInto(base);
        assertEquals(1, replaced.triggers().size());
        assertEquals(MasteryTrigger.KILL, replaced.triggers().iterator().next());
        assertEquals(1, replaced.shares().size(),
                "the overlay's share list replaces the base list entirely");
        assertTrue(replaced.effects().isEmpty(),
                "an empty effects array removes every effect axis");
        assertEquals(10.0, replaced.baseGain(), "absent keeps the base gain");
    }

    @Test
    void mergedSpecsPassTheSpecValidation() {
        // decode-valid input must always produce a spec the base contract accepts
        var merged = decode("""
                { "shares": [{"kind": "CATEGORY", "id": "tensura:fire", "share": 1.0}] }""")
                .mergeInto(base());
        assertEquals(1, merged.shares().size());
        assertEquals(1.0, merged.shares().get(0).share(), 1e-9);
    }
}
