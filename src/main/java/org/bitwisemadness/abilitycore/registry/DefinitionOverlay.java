package org.bitwisemadness.abilitycore.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.jetbrains.annotations.Nullable;

/**
 * A datapack's per-id definition overlay (issue #12's data-map sub-part,
 * REQ-BW-07's datapack override path). Every field mirrors
 * {@link AbilityDefinition}'s JSON shape and is optional: a present value
 * replaces the code-registered base at lookup, an absent one keeps it, and
 * {@code data} merges per-key (overlay wins conflicts, base keys are kept).
 * {@code tags} replaces wholesale when present.
 *
 * <p>The JSON here is the public contract (see {@code docs/datapack-overrides.md});
 * this Java type is internal per {@code docs/api-surface.md}. An overlay can
 * only ever <em>modify</em> an id that is registered in code - the loader
 * drops overlays naming unknown ids, so a datapack cannot resurrect a removed
 * ability.</p>
 */
public record DefinitionOverlay(
        @Nullable String displayNameKey,
        @Nullable ResourceLocation iconId,
        @Nullable ResourceLocation category,
        @Nullable ActivationType activationType,
        @Nullable ResourceCost cost,
        @Nullable Set<String> tags,
        @Nullable Map<String, String> data) {

    /** Set codec for the open tag vocabulary (this DFU build has no Codec.unboundedSet). */
    private static final Codec<Set<String>> TAG_SET_CODEC =
            Codec.list(Codec.STRING).xmap(Set::copyOf, List::copyOf);

    /** Every field optional - an empty overlay is a legal no-op. */
    public static final Codec<DefinitionOverlay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("display_name")
                    .forGetter(o -> Optional.ofNullable(o.displayNameKey())),
            ResourceLocation.CODEC.optionalFieldOf("icon")
                    .forGetter(o -> Optional.ofNullable(o.iconId())),
            ResourceLocation.CODEC.optionalFieldOf("category")
                    .forGetter(o -> Optional.ofNullable(o.category())),
            ActivationType.CODEC.optionalFieldOf("activation_type")
                    .forGetter(o -> Optional.ofNullable(o.activationType())),
            ResourceCost.CODEC.optionalFieldOf("cost")
                    .forGetter(o -> Optional.ofNullable(o.cost())),
            TAG_SET_CODEC.optionalFieldOf("tags")
                    .forGetter(o -> Optional.ofNullable(o.tags())),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("data")
                    .forGetter(o -> Optional.ofNullable(o.data()))
    ).apply(instance, DefinitionOverlay::create));

    @SuppressWarnings("unused") // adapter for optional codec fields -> record
    private static DefinitionOverlay create(
            Optional<String> displayNameKey,
            Optional<ResourceLocation> iconId,
            Optional<ResourceLocation> category,
            Optional<ActivationType> activationType,
            Optional<ResourceCost> cost,
            Optional<Set<String>> tags,
            Optional<Map<String, String>> data) {
        return new DefinitionOverlay(
                displayNameKey.orElse(null), iconId.orElse(null), category.orElse(null),
                activationType.orElse(null), cost.orElse(null), tags.orElse(null),
                data.orElse(null));
    }

    public DefinitionOverlay {
        if (displayNameKey != null && displayNameKey.isBlank()) {
            throw new IllegalArgumentException("overlay display name key must not be blank");
        }
        if (tags != null) {
            tags = Set.copyOf(tags);
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) {
                    throw new IllegalArgumentException("overlay tags must not be blank");
                }
            }
        }
        if (data != null) {
            data = Map.copyOf(data);
            data.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("overlay data keys must not be blank");
                }
            });
        }
    }

    /**
     * Applies this overlay to {@code base} at lookup: present fields replace,
     * absent fields keep the base, {@code data} merges per-key (this overlay
     * wins conflicts), {@code tags} replaces wholesale. A no-op overlay
     * produces a value-equal definition.
     */
    public AbilityDefinition mergeInto(AbilityDefinition base) {
        Map<String, String> mergedData = new HashMap<>(base.data());
        if (data != null) {
            mergedData.putAll(data);
        }
        return new AbilityDefinition(
                displayNameKey != null ? displayNameKey : base.displayNameKey(),
                iconId != null ? iconId : base.iconId(),
                category != null ? category : base.category(),
                activationType != null ? activationType : base.activationType(),
                cost != null ? cost : base.cost(),
                tags != null ? tags : base.tags(),
                mergedData);
    }
}
