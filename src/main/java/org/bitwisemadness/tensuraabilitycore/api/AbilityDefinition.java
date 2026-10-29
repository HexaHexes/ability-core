package org.bitwisemadness.tensuraabilitycore.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Provider-independent description of a single ability (REQ-CORE-01).
 *
 * <p>Instances live in the core-owned {@code tensura_ability_core:abilities}
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
        Map<String, String> data) {

    public static final Codec<AbilityDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(AbilityDefinition::displayNameKey),
            ResourceLocation.CODEC.optionalFieldOf("icon")
                    .forGetter(d -> Optional.ofNullable(d.iconId())),
            ResourceLocation.CODEC.fieldOf("category").forGetter(AbilityDefinition::category),
            ActivationType.CODEC.fieldOf("activation_type").forGetter(AbilityDefinition::activationType),
            ResourceCost.CODEC.optionalFieldOf("cost")
                    .forGetter(d -> Optional.ofNullable(d.cost())),
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
            Map<String, String> data) {
        return new AbilityDefinition(
                displayNameKey, iconId.orElse(null), category, activationType, cost.orElse(null), data);
    }

    public AbilityDefinition {
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(activationType, "activationType");
        // defensive copy: definitions are immutable value objects shared across threads
        data = Map.copyOf(Objects.requireNonNull(data, "data"));
        if (displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank");
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
    }
}
