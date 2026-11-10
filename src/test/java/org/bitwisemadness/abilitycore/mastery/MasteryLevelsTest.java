package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The datapack level table (REQ-CORE-12): parse validation (fail to the
 * formula), the exactly-one install rule (conflicts never pick
 * silently), and the table's level reads - authoritative over the
 * config formula while installed, degenerate inputs reading as 0.
 */
class MasteryLevelsTest {

    private static final ResourceLocation ONE =
            ResourceLocation.fromNamespaceAndPath("pack_a", "mastery/levels.json");
    private static final ResourceLocation TWO =
            ResourceLocation.fromNamespaceAndPath("pack_b", "mastery/levels.json");

    @AfterEach
    void isolate() {
        MasteryLevels.installForTests(null);
    }

    private static double[] parse(String json) {
        return MasteryLevels.parseLevels(JsonParser.parseString(json));
    }

    @Test
    void parsesAnAscendingTable() {
        assertArrayEquals(new double[] {100.0, 250.0, 450.0},
                parse("{ \"levels\": [100, 250, 450] }"));
    }

    @Test
    void rejectsBadTablesLoudly() {
        assertThrows(IllegalArgumentException.class, () -> parse("{}"),
                "missing levels array");
        assertThrows(IllegalArgumentException.class, () -> parse("{ \"levels\": [] }"),
                "empty means use the formula instead");
        assertThrows(IllegalArgumentException.class,
                () -> parse("{ \"levels\": [100, 50] }"), "must strictly ascend");
        assertThrows(IllegalArgumentException.class,
                () -> parse("{ \"levels\": [100, 100] }"), "no duplicates");
        assertThrows(IllegalArgumentException.class,
                () -> parse("{ \"levels\": [-5] }"), "no negatives");
        assertThrows(IllegalArgumentException.class,
                () -> parse("{ \"levels\": [\"lots\"] }"), "numbers only");
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> parse("{ \"levels\": [100, 250, 450]"), "not even JSON");
    }

    @Test
    void applyTablesFollowsTheExactlyOneRule() {
        assertFalse(MasteryLevels.hasTable(), "starts on the formula");

        MasteryLevels.applyTables(new LinkedHashMap<>());
        assertFalse(MasteryLevels.hasTable(), "zero tables = formula");

        MasteryLevels.applyTables(Map.of(ONE, new double[] {100.0, 250.0}));
        assertTrue(MasteryLevels.hasTable(), "one table wins");

        MasteryLevels.applyTables(Map.of(ONE, new double[] {100.0},
                TWO, new double[] {50.0}));
        assertFalse(MasteryLevels.hasTable(),
                "a conflict never picks silently - back to the formula");
    }

    @Test
    void tableLevelsAreAuthoritative() {
        MasteryLevels.installForTests(new double[] {100.0, 250.0});
        assertTrue(MasteryLevels.hasTable());
        assertEquals(0, MasteryLevels.level(0.0));
        assertEquals(0, MasteryLevels.level(99.0));
        assertEquals(1, MasteryLevels.level(100.0));
        assertEquals(1, MasteryLevels.level(249.0));
        assertEquals(2, MasteryLevels.level(250.0));
        assertEquals(2, MasteryLevels.level(1_000_000.0),
                "past the table the level pins at its length - the table owns the cap");
        assertEquals(0, MasteryLevels.level(Double.NaN), "degenerate reads as 0");
    }

    @Test
    void withoutATableTheConfigFormulaAnswers() {
        MasteryLevels.installForTests(null);
        assertFalse(MasteryLevels.hasTable());
        assertEquals(0, MasteryLevels.level(0.0));
        // config fallback with no loaded config: base 100, growth 1.15 -
        // one full base step is always one level
        assertEquals(1, MasteryLevels.level(100.0));
        assertTrue(MasteryLevels.level(10_000.0) > 1, "the formula keeps leveling");
    }
}
