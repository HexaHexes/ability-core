package org.bitwisemadness.abilitycore.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Provider-independent description of a single ability (REQ-CORE-01).
 *
 * <p>Instances live in the core-owned {@code ability_core:abilities}
 * registry; providers register entries at registry time on their own mod bus
 * via {@code RegisterEvent}. The registry key is exported from
 * {@code AbilityRegistries}.</p>
 *
 * <p>Categorisation is deliberately an <em>open</em> vocabulary: a namespaced
 * {@code category} plus an open {@code tags} set, never a closed enum - the
 * four known kinds (skill, magic, spell, battlewill) and future providers all
 * fit without core changes. Filtering is set-based over these fields;
 * vanilla {@code TagKey} machinery on modded built-in registries was not
 * verified for 1.21.1 and is intentionally not relied upon (recorded in the
 * PR for issue #12).</p>
 *
 * <p>Validity is enforced by a registry {@code onAdd} callback (also runs on
 * client-side sync rebuilds): {@link #validateOrThrow(ResourceLocation)}.</p>
 *
 * @param displayNameKey translation key for player-facing display
 * @param iconId         icon resource referenced by location at runtime
 *                       (ARR upstreams: referenced, never copied - ADR-005),
 *                       null if the provider supplies no static icon
 * @param category       open namespaced category (e.g. {@code tensura:magic},
 *                       {@code irons_spellbooks:spell}, {@code tensura:battlewill})
 * @param activationType how the ability activates; {@link ActivationType#UNKNOWN}
 *                       is permitted in data and refused at activation (fail-closed)
 * @param cost           declared resource-cost metadata, or null when the
 *                       provider owns costs without declaring them yet
 * @param tags           open flat tag vocabulary for filtering (e.g.
 *                       {@code "unarmed"}, {@code "passive"}, {@code "melee"});
 *                       delivered with issue #14's enumeration API as the
 *                       first-class replacement for the interim data-slot idea
 * @param data           open string key/value slots for provider- or
 *                       datapack-supplied metadata (feeds the data-map overlay,
 *                       see ADR-001)
 */
public record AbilityDefinition(
        String displayNameKey,
        @Nullable ResourceLocation iconId,
        ResourceLocation category,
        ActivationType activationType,
        @Nullable ResourceCost cost,
        Set<String> tags,
        Map<String, String> data) {

    /** Set codec for the open tag vocabulary (this DFU build has no Codec.unboundedSet). */
    private static final Codec<Set<String>> TAG_SET_CODEC =
            Codec.list(Codec.STRING).xmap(Set::copyOf, List::copyOf);

    public static final Codec<AbilityDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(AbilityDefinition::displayNameKey),
            ResourceLocation.CODEC.optionalFieldOf("icon")
                    .forGetter(d -> Optional.ofNullable(d.iconId())),
            ResourceLocation.CODEC.fieldOf("category").forGetter(AbilityDefinition::category),
            ActivationType.CODEC.fieldOf("activation_type").forGetter(AbilityDefinition::activationType),
            ResourceCost.CODEC.optionalFieldOf("cost")
                    .forGetter(d -> Optional.ofNullable(d.cost())),
            TAG_SET_CODEC.optionalFieldOf("tags", Set.of())
                    .forGetter(AbilityDefinition::tags),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("data", Map.of())
                    .forGetter(AbilityDefinition::data)
    ).apply(instance, AbilityDefinition::create));

    @SuppressWarnings("unused") // adapter for optional codec fields -> record
    private static AbilityDefinition create(
            String displayNameKey,
            Optional<ResourceLocation> iconId,
            ResourceLocation category,
            ActivationType activationType,
            Optional<ResourceCost> cost,
            Set<String> tags,
            Map<String, String> data) {
        return new AbilityDefinition(
                displayNameKey, iconId.orElse(null), category, activationType, cost.orElse(null), tags, data);
    }

    public AbilityDefinition {
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(activationType, "activationType");
        // defensive copies: definitions are immutable value objects shared across threads
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        data = Map.copyOf(Objects.requireNonNull(data, "data"));
        if (displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank");
        }
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("definition tags must not be blank");
            }
        }
        data.forEach((k, v) -> {
            if (k == null || k.isBlank()) {
                throw new IllegalArgumentException("definition data keys must not be blank");
            }
        });
    }

    /**
     * Registration-time validity gate, wired as the registry's {@code onAdd}
     * callback (also invoked when a client rebuilds the registry from a server
     * sync, so both sides validate identically).
     *
     * @param id the registry id being written, for error reporting
     * @throws IllegalStateException if the definition is malformed
     */
    public void validateOrThrow(ResourceLocation id) {
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalStateException("Invalid ability definition " + id + ": blank display name key");
        }
        if (category == null) {
            throw new IllegalStateException("Invalid ability definition " + id + ": missing category");
        }
        if (activationType == null) {
            throw new IllegalStateException("Invalid ability definition " + id + ": missing activation type");
        }
        if (cost != null) {
            if (cost.resourceId().isBlank() || cost.amount() < 0 || !Double.isFinite(cost.amount())) {
                throw new IllegalStateException("Invalid ability definition " + id + ": malformed cost " + cost);
            }
        }
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                throw new IllegalStateException("Invalid ability definition " + id + ": blank tag");
            }
        }
    }
}
