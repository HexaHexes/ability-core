package org.bitwisemadness.abilitycore.mastery;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * One player's stored mastery XP (REQ-CORE-12): raw XP per
 * {@link org.bitwisemadness.abilitycore.api.MasteryTarget#key()}, levels
 * derived on read through the curve - never stored, so a curve config
 * change re-levels every player on the next read.
 *
 * <p>Deliberately config-free (the same rule as hotbar's bar-state
 * serializer): the attachment must load before/without a loaded config.
 * Schema v1 is a flat {@code key -> double} compound; there is no v0.</p>
 */
public final class MasteryData {

    /** The NBT schema version this build writes. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String KEY_SCHEMA = "schema_version";

    private final Map<String, Double> xp;

    /** An empty store (fresh player, or a failed load failing closed). */
    public MasteryData() {
        this.xp = new HashMap<>();
    }

    /**
     * Adds XP to one target key (validated upstream; non-finite or
     * non-positive amounts are ignored rather than corrupting the store).
     *
     * @param key    the {@code KIND|id} target key
     * @param amount XP to add
     */
    public void addXp(String key, double amount) {
        if (key == null || key.isBlank() || !Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        xp.merge(key, amount, Double::sum);
    }

    /**
     * Sets one target's XP (the admin command's write): validated like
     * {@link #addXp} - non-finite or negative never corrupts the store -
     * and 0 clears the key (an absent key reads 0 anyway).
     *
     * @param key    the {@code KIND|id} target key
     * @param amount the XP to hold
     */
    public void setXp(String key, double amount) {
        if (key == null || key.isBlank() || !Double.isFinite(amount) || amount < 0.0) {
            return;
        }
        if (amount == 0.0) {
            xp.remove(key);
            return;
        }
        xp.put(key, amount);
    }

    /** @return the XP stored on one target key (0 when absent) */
    public double xp(String key) {
        if (key == null) {
            return 0.0;
        }
        return xp.getOrDefault(key, 0.0);
    }

    /** @return an unmodifiable snapshot of every stored target key */
    public Map<String, Double> entries() {
        return Map.copyOf(xp);
    }

    /** Writes schema v1: version key plus the flat key/xp pairs. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_SCHEMA, CURRENT_SCHEMA_VERSION);
        for (Map.Entry<String, Double> entry : xp.entrySet()) {
            tag.putDouble(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    /**
     * Reads schema v1. A newer schema loads as empty (forward-only:
     * better a fresh store than a misparsed one), and unparseable values
     * are skipped - corrupt data fails closed, never crashes.
     *
     * @param tag the stored compound
     * @return the loaded store (possibly empty)
     */
    public static MasteryData load(CompoundTag tag) {
        MasteryData data = new MasteryData();
        if (tag == null) {
            return data;
        }
        int version = tag.contains(KEY_SCHEMA) ? tag.getInt(KEY_SCHEMA) : CURRENT_SCHEMA_VERSION;
        if (version > CURRENT_SCHEMA_VERSION) {
            return data;
        }
        for (String key : tag.getAllKeys()) {
            if (KEY_SCHEMA.equals(key) || !tag.contains(key)) {
                continue;
            }
            if (tag.getTagType(key) == CompoundTag.TAG_DOUBLE) {
                data.addXp(key, tag.getDouble(key));
            }
        }
        return data;
    }
}
