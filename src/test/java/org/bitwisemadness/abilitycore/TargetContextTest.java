package org.bitwisemadness.abilitycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.bitwisemadness.abilitycore.api.TargetContext;
import org.junit.jupiter.api.Test;

class TargetContextTest {

    @Test
    void wireParsesEveryKind() {
        assertEquals(TargetContext.Kind.NONE,
                TargetContext.fromWire("none", Map.of()).orElseThrow().kindOf());
        assertEquals(TargetContext.Kind.SELF,
                TargetContext.fromWire("SELF", Map.of()).orElseThrow().kindOf());
        var entity = TargetContext.fromWire("entity",
                Map.of("entity", UUID.randomUUID().toString())).orElseThrow();
        assertTrue(entity instanceof TargetContext.EntityHint);
        var block = TargetContext.fromWire("block",
                Map.of("x", "10", "y", "64", "z", "-3")).orElseThrow();
        assertEquals(new BlockPos(10, 64, -3), ((TargetContext.BlockHint) block).pos());
        var area = TargetContext.fromWire("area",
                Map.of("x", "0", "y", "64", "z", "0", "radius", "4.5")).orElseThrow();
        assertEquals(4.5, ((TargetContext.AreaHint) area).radius());
    }

    @Test
    void wireFailsClosedOnMalformedInput() {
        assertTrue(TargetContext.fromWire("sideways", Map.of()).isEmpty());
        assertTrue(TargetContext.fromWire(null, Map.of()).isEmpty());
        assertTrue(TargetContext.fromWire("entity", Map.of("entity", "not-a-uuid")).isEmpty());
        assertTrue(TargetContext.fromWire("entity", Map.of()).isEmpty());
        assertTrue(TargetContext.fromWire("block", Map.of("x", "1", "y", "2")).isEmpty());
        assertTrue(TargetContext.fromWire("block", Map.of("x", "a", "y", "2", "z", "3")).isEmpty());
        assertTrue(TargetContext.fromWire("area",
                Map.of("x", "0", "y", "0", "z", "0", "radius", "-1")).isEmpty());
        assertTrue(TargetContext.fromWire("area",
                Map.of("x", "0", "y", "0", "z", "0", "radius", "NaN")).isEmpty());
    }

    @Test
    void hintsAreImmutableAndGuardsFire() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TargetContext.EntityHint(null));
        assertThrows(IllegalArgumentException.class, () -> new TargetContext.BlockHint(null));
        assertThrows(IllegalArgumentException.class, () -> new TargetContext.AreaHint(null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TargetContext.AreaHint(new BlockPos(0, 0, 0), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TargetContext.AreaHint(new BlockPos(0, 0, 0), Double.NaN));
        assertEquals(TargetContext.Kind.ENTITY, new TargetContext.EntityHint(id).kindOf());
        assertEquals(TargetContext.Kind.BLOCK, new TargetContext.BlockHint(new BlockPos(1, 2, 3)).kindOf());
        assertEquals(TargetContext.Kind.AREA, new TargetContext.AreaHint(new BlockPos(1, 2, 3), 2).kindOf());
        assertEquals(TargetContext.Kind.NONE, new TargetContext.None().kindOf());
        assertEquals(TargetContext.Kind.SELF, new TargetContext.Self().kindOf());
        assertEquals(TargetContext.Kind.NONE, TargetContext.kindOfNullable(null));
        Optional<TargetContext> absent = Optional.empty();
        assertTrue(absent.isEmpty());
    }
}
