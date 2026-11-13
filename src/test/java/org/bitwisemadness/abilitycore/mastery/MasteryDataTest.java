package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The mastery store's NBT round trip (REQ-CORE-12): accumulation, the
 * write/read identity, and the fail-closed load rules (newer schema and
 * corrupt values load as empty/skipped, never crash).
 */
class MasteryDataTest {

    private static final String KEY = "ABILITY|tensura:probe";

    @Test
    void addXpAccumulatesPerKeyAndGuardsBadInput() {
        MasteryData data = new MasteryData();
        data.addXp(KEY, 10.0);
        data.addXp(KEY, 5.0);
        assertEquals(15.0, data.xp(KEY), 1e-9, "XP accumulates on one target");
        assertEquals(0.0, data.xp("ABILITY|tensura:absent"), 1e-9, "absent key reads 0");

        data.addXp(KEY, Double.NaN);
        data.addXp(KEY, -100.0);
        data.addXp(null, 10.0);
        data.addXp("  ", 10.0);
        assertEquals(15.0, data.xp(KEY), 1e-9, "invalid writes never corrupt the store");
    }

    @Test
    void entriesAreAnUnmodifiableSnapshot() {
        MasteryData data = new MasteryData();
        data.addXp(KEY, 1.0);
        assertEquals(1, data.entries().size());
        assertThrows(UnsupportedOperationException.class,
                () -> data.entries().put("x", 1.0), "callers read, never mutate");
    }

    @Test
    void saveAndLoadRoundTrip() {
        MasteryData data = new MasteryData();
        data.addXp(KEY, 12.5);
        data.addXp("CATEGORY|tensura:fire", 3.0);

        MasteryData loaded = MasteryData.load(data.save());
        assertEquals(12.5, loaded.xp(KEY), 1e-9);
        assertEquals(3.0, loaded.xp("CATEGORY|tensura:fire"), 1e-9);
        assertEquals(2, loaded.entries().size());
    }

    @Test
    void loadFailsClosedOnMissingNewerOrCorruptData() {
        assertEquals(0, MasteryData.load(null).entries().size(), "null tag = empty store");

        CompoundTag newer = new CompoundTag();
        newer.putInt("schema_version", MasteryData.CURRENT_SCHEMA_VERSION + 1);
        newer.putDouble(KEY, 99.0);
        assertEquals(0, MasteryData.load(newer).entries().size(),
                "a future schema loads empty - forward-only, never misparsed");

        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("schema_version", 1);
        corrupt.putString(KEY, "not a number");
        corrupt.putDouble("CATEGORY|tensura:fire", 7.0);
        MasteryData loaded = MasteryData.load(corrupt);
        assertEquals(0.0, loaded.xp(KEY), 1e-9, "a non-double value is skipped");
        assertEquals(7.0, loaded.xp("CATEGORY|tensura:fire"), 1e-9, "valid keys survive");
        assertTrue(loaded.entries().containsKey("CATEGORY|tensura:fire"));
    }

    @Test
    void setXpOverwritesAndClears() {
        // the admin command's write (owner's brief): set replaces the
        // pool outright, zero clears the key, invalid writes never corrupt
        MasteryData data = new MasteryData();
        data.addXp(KEY, 10.0);
        data.setXp(KEY, 99.5);
        assertEquals(99.5, data.xp(KEY), 1e-9, "set overwrites the pool");
        data.setXp(KEY, 0.0);
        assertEquals(0.0, data.xp(KEY), 1e-9, "zero clears the key");
        assertTrue(data.entries().isEmpty(), "a cleared key leaves the store");

        data.setXp(KEY, -5.0);
        data.setXp(KEY, Double.NaN);
        data.setXp(null, 7.0);
        data.setXp("  ", 7.0);
        assertEquals(0.0, data.xp(KEY), 1e-9, "invalid writes never corrupt");
    }
}
