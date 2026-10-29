package org.bitwisemadness.tensuraabilitycore.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/**
 * Declarative resource-cost descriptor on an ability definition.
 *
 * <p>This is *metadata about* a cost, not a charging mechanism: the actual
 * charge site is owned by the provider's {@code chargingModel}
 * (ADR-002, REQ-CORE-04). The overlay/validation layers read this descriptor;
 * nothing charges from it directly at this stage.</p>
 *
 * @param resourceId namespaced resource identifier (e.g. {@code tensura:aura},
 *                   {@code irons_spellbooks:mana}) - must be a legal path
 * @param amount     non-negative, finite declared amount
 */
public record ResourceCost(String resourceId, double amount) {

    public static final Codec<ResourceCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("resource").forGetter(ResourceCost::resourceId),
            Codec.DOUBLE.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(instance, ResourceCost::new));

    public ResourceCost {
        Objects.requireNonNull(resourceId, "resourceId");
        // fail fast on construction: a malformed cost must never become data
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resource id must not be blank");
        }
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException(
                    "cost amount must be finite and >= 0, got " + amount + " for resource " + resourceId);
        }
    }
}
