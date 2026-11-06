package org.bitwisemadness.abilitycore.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/**
 * One declared stat of an ability (issue #119): an ordered, player-presented
 * fact the menu shows in the inline tooltip - damage, range, cooldown, aura
 * of effect. The declaration order is the presentation order.
 *
 * <p>Labels are provider-owned translation keys (the {@code displayNameKey}
 * rule: core never invents player strings, consumers resolve and fall back);
 * values are literal presentation text ({@code "200"}, {@code "3s"},
 * {@code "15 m"}) and are never parsed into anything authoritative.</p>
 *
 * @param labelKey translation key for the stat's name
 * @param value    the literal presentation value
 */
public record Stat(String labelKey, String value) {

    /** Wire shape: {@code {"label": "...", "value": "..."}} entries. */
    public static final Codec<Stat> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("label").forGetter(Stat::labelKey),
            Codec.STRING.fieldOf("value").forGetter(Stat::value)
    ).apply(instance, Stat::new));

    public Stat {
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(value, "value");
        if (labelKey.isBlank()) {
            throw new IllegalArgumentException("stat label key must not be blank");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("stat value must not be blank");
        }
    }
}
