package org.bitwisemadness.abilitycore.api;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
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
 * @param costs          declared resource-cost entries (issue #89's multi-cost
 *                       list, validated all-or-nothing), empty when the
 *                       provider owns costs without declaring them yet;
 *                       never null
 * @param consumptionStrategy name of the registered function deciding how the
 *                       costs are consumed (issue #90; {@code all},
 *                       {@code random}, {@code in_order} or an addon's
 *                       declared strategy); resolved at registration - an
 *                       unknown name fails loudly
 * @param amountResolver  name of the registered function pricing the costs
 *                       for one activation (issue #103; {@code static} or an
 *                       addon's declared resolver); resolved at registration
 *                       - an unknown name fails loudly
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
        List<ResourceCost> costs,
        String consumptionStrategy,
        String amountResolver,
        Set<String> tags,
        Map<String, String> data) {

    /** Set codec for the open tag vocabulary (this DFU build has no Codec.unboundedSet). */
    private static final Codec<Set<String>> TAG_SET_CODEC =
            Codec.list(Codec.STRING).xmap(Set::copyOf, List::copyOf);

    private static final Codec<AbilityDefinition> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(AbilityDefinition::displayNameKey),
            ResourceLocation.CODEC.optionalFieldOf("icon")
                    .forGetter(d -> Optional.ofNullable(d.iconId())),
            ResourceLocation.CODEC.fieldOf("category").forGetter(AbilityDefinition::category),
            ActivationType.CODEC.fieldOf("activation_type").forGetter(AbilityDefinition::activationType),
            ResourceCost.CODEC.listOf().optionalFieldOf("costs", List.of())
                    .forGetter(AbilityDefinition::costs),
            Codec.STRING.optionalFieldOf("consumption_strategy", ConsumptionStrategies.ALL)
                    .forGetter(AbilityDefinition::consumptionStrategy),
            Codec.STRING.optionalFieldOf("amount_resolver", AmountResolvers.STATIC)
                    .forGetter(AbilityDefinition::amountResolver),
            TAG_SET_CODEC.optionalFieldOf("tags", Set.of())
                    .forGetter(AbilityDefinition::tags),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("data", Map.of())
                    .forGetter(AbilityDefinition::data)
    ).apply(instance, AbilityDefinition::create));

    /**
     * The definition codec: a {@code costs} array (issue #89). The legacy
     * single {@code cost} object is <b>rejected</b> loudly - DFU would
     * silently ignore the unknown key, turning a datapack author's cost
     * change into a no-op (fail-silent, against the project's posture).
     */
    public static final Codec<AbilityDefinition> CODEC = rejectingLegacyCost(RECORD_CODEC);

    private static Codec<AbilityDefinition> rejectingLegacyCost(Codec<AbilityDefinition> inner) {
        return Codec.of(inner, new Decoder<AbilityDefinition>() {
            @Override
            public <T> DataResult<Pair<AbilityDefinition, T>> decode(DynamicOps<T> ops, T input) {
                if (new Dynamic<>(ops, input).get("cost").result().isPresent()) {
                    return DataResult.error(
                            () -> "Legacy 'cost' key removed (issue #89): use a 'costs' array");
                }
                return inner.decode(ops, input);
            }
        });
    }

    @SuppressWarnings("unused") // adapter for optional codec fields -> record
    private static AbilityDefinition create(
            String displayNameKey,
            Optional<ResourceLocation> iconId,
            ResourceLocation category,
            ActivationType activationType,
            List<ResourceCost> costs,
            String consumptionStrategy,
            String amountResolver,
            Set<String> tags,
            Map<String, String> data) {
        return new AbilityDefinition(
                displayNameKey, iconId.orElse(null), category, activationType, costs,
                consumptionStrategy, amountResolver, tags, data);
    }

    public AbilityDefinition {
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(activationType, "activationType");
        Objects.requireNonNull(consumptionStrategy, "consumptionStrategy");
        Objects.requireNonNull(amountResolver, "amountResolver");
        // defensive copies: definitions are immutable value objects shared across threads
        costs = List.copyOf(Objects.requireNonNull(costs, "costs"));
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        data = Map.copyOf(Objects.requireNonNull(data, "data"));
        if (displayNameKey.isBlank()) {
            throw new IllegalArgumentException("display name key must not be blank");
        }
        if (consumptionStrategy.isBlank()) {
            throw new IllegalArgumentException("consumption strategy must not be blank");
        }
        if (amountResolver.isBlank()) {
            throw new IllegalArgumentException("amount resolver must not be blank");
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
        if (costs != null) {
            for (ResourceCost cost : costs) {
                if (cost.resourceId().isBlank() || cost.amount() < 0 || !Double.isFinite(cost.amount())) {
                    throw new IllegalStateException(
                            "Invalid ability definition " + id + ": malformed cost " + cost);
                }
            }
        }
        if (!ConsumptionStrategies.has(consumptionStrategy)) {
            // timing contract: strategies declare at construction, definitions
            // register at RegisterEvent - an unknown name here is a wiring bug
            throw new IllegalStateException("Invalid ability definition " + id
                    + ": unknown consumption strategy '" + consumptionStrategy + "'");
        }
        if (!AmountResolvers.has(amountResolver)) {
            // same timing contract as the strategy check (issue #103)
            throw new IllegalStateException("Invalid ability definition " + id
                    + ": unknown amount resolver '" + amountResolver + "'");
        }
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                throw new IllegalStateException("Invalid ability definition " + id + ": blank tag");
            }
        }
    }
}
