package org.bitwisemadness.abilitycore.api;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Where one ability's experience flows and whose levels empower it
 * (REQ-CORE-12): a spec's share list names targets of these kinds, and the
 * same targets are aggregated back into the caster's effect view. Three
 * kinds cover the contract - the ability itself is the implicit
 * self-remainder, another ability (including a dedicated mastery vessel)
 * is {@link Kind#ABILITY}, and the two grouping levels are
 * {@link Kind#CATEGORY} / {@link Kind#SUBCATEGORY}.
 *
 * @param kind the grouping this target names
 * @param id  the ability, category or subcategory id
 */
public record MasteryTarget(Kind kind, ResourceLocation id) {

    /** The kinds a mastery target can name. */
    public enum Kind {
        /** An ability - another spell/technique, or a dedicated mastery vessel. */
        ABILITY,
        /** A category: the {@code AbilityDefinition.category()} grouping. */
        CATEGORY,
        /** A subcategory: finer than category, declared on the mastery spec. */
        SUBCATEGORY
    }

    public MasteryTarget {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null for " + kind);
        }
    }

    /** The ability target: this id's own mastery, or a dedicated vessel. */
    public static MasteryTarget ability(ResourceLocation id) {
        return new MasteryTarget(Kind.ABILITY, id);
    }

    /** The category target: the generic mastery of an ability category. */
    public static MasteryTarget category(ResourceLocation id) {
        return new MasteryTarget(Kind.CATEGORY, id);
    }

    /** The subcategory target: the generic mastery of a subcategory. */
    public static MasteryTarget subcategory(ResourceLocation id) {
        return new MasteryTarget(Kind.SUBCATEGORY, id);
    }

    /**
     * The storage key for this target: {@code KIND|namespace:path}. The
     * separator is {@code |}, which a {@code ResourceLocation} can never
     * contain, so {@link #parseKey(String)} round-trips every valid target.
     *
     * @return the stable key used in player NBT and query payloads
     */
    public String key() {
        return kind.name() + "|" + id;
    }

    /**
     * The inverse of {@link #key()}.
     *
     * @param key a key produced by {@code key()}
     * @return the parsed target, or null when the key is malformed
     */
    @Nullable
    public static MasteryTarget parseKey(String key) {
        if (key == null) {
            return null;
        }
        int split = key.indexOf('|');
        if (split <= 0 || split == key.length() - 1) {
            return null;
        }
        Kind kind;
        try {
            kind = Kind.valueOf(key.substring(0, split));
        } catch (IllegalArgumentException e) {
            return null;
        }
        try {
            return new MasteryTarget(kind, ResourceLocation.parse(key.substring(split + 1)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Lowercase kind name for config/datapack-facing strings. */
    public String kindName() {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
