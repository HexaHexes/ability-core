package org.bitwisemadness.abilitycore.mastery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.jetbrains.annotations.Nullable;

/**
 * A datapack's per-id mastery-spec overlay (REQ-CORE-12, the data-map
 * layer): every field optional, riding the built-in ability registry like
 * {@code DefinitionOverlay}. A present field replaces the code-declared
 * base wholesale at lookup, an absent one keeps it; the overlay can only
 * ever <em>tune</em> a spec declared in code - an id with no declaration
 * has no system at all, so an overlay never creates one (opt-in stays
 * single-sourced).
 *
 * <p>The JSON here is the public contract (see
 * {@code docs/datapack-overrides.md}); this Java type is internal per
 * {@code docs/api-surface.md}. Unknown triggers/kinds/axes and invalid
 * shares fail <b>at decode</b> (the data-map load logs and the overlay is
 * dropped), so a bad file fails to the base spec - never to a crash and
 * never to a half-applied value.</p>
 *
 * @param baseGain   replaces the base gain when present
 * @param gainHandler replaces the base gain handler when present (a name
 *                    resolving at first use, like every handler name)
 * @param triggers   replaces the trigger set wholesale when present
 * @param shares     replaces the share list wholesale when present
 * @param effects    replaces the participating-axis map wholesale when
 *                   present - an empty array removes every effect
 */
public record MasterySpecOverlay(
        @Nullable Double baseGain,
        @Nullable String gainHandler,
        @Nullable List<MasteryTrigger> triggers,
        @Nullable List<Share> shares,
        @Nullable List<EffectEntry> effects) {

    /** One share in JSON shape: {@code {"kind": ..., "id": ..., "share": ...}}. */
    public record Share(MasteryTarget.Kind kind, ResourceLocation id, double share) {

        public Share {
            if (kind == null) {
                throw new IllegalArgumentException("share kind must not be null");
            }
            if (id == null) {
                throw new IllegalArgumentException("share id must not be null");
            }
            if (!Double.isFinite(share) || share <= 0.0 || share > 1.0) {
                throw new IllegalArgumentException(
                        "share must be in (0, 1], got " + share + " for " + kind + " " + id);
            }
        }

        MasterySpec.Share toSpecShare() {
            return new MasterySpec.Share(new MasteryTarget(kind, id), share);
        }
    }

    /**
     * One participating axis in JSON shape:
     * {@code {"axis": "COST", "handler": "mymod:x"}} - a missing handler
     * means core's default curve for that axis.
     */
    public record EffectEntry(MasteryAxis axis, @Nullable String handler) {

        public EffectEntry {
            if (axis == null) {
                throw new IllegalArgumentException("effect axis must not be null");
            }
            if (handler != null && handler.isBlank()) {
                throw new IllegalArgumentException("effect handler must not be blank");
            }
        }

        static EffectEntry create(MasteryAxis axis, Optional<String> handler) {
            return new EffectEntry(axis, handler.orElse(null));
        }

        MasterySpec.EffectRule toRule() {
            return new MasterySpec.EffectRule(handler);
        }
    }

    private static final Codec<MasteryTrigger> TRIGGER_CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(MasteryTrigger.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "unknown mastery trigger '" + name + "'");
                }
            },
            trigger -> DataResult.success(trigger.name()));

    private static final Codec<MasteryTarget.Kind> KIND_CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(MasteryTarget.Kind.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "unknown mastery target kind '" + name + "'");
                }
            },
            kind -> DataResult.success(kind.name()));

    private static final Codec<MasteryAxis> AXIS_CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(MasteryAxis.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "unknown mastery axis '" + name + "'");
                }
            },
            axis -> DataResult.success(axis.name()));

    private static final Codec<Share> SHARE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            KIND_CODEC.fieldOf("kind").forGetter(Share::kind),
            ResourceLocation.CODEC.fieldOf("id").forGetter(Share::id),
            Codec.DOUBLE.fieldOf("share").forGetter(Share::share)
    ).apply(instance, Share::new));

    private static final Codec<EffectEntry> EFFECT_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    AXIS_CODEC.fieldOf("axis").forGetter(EffectEntry::axis),
                    Codec.STRING.optionalFieldOf("handler")
                            .forGetter(entry -> Optional.ofNullable(entry.handler()))
            ).apply(instance, EffectEntry::create));

    private static final Codec<MasterySpecOverlay> RECORD_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("base_gain")
                            .forGetter(o -> Optional.ofNullable(o.baseGain())),
                    Codec.STRING.optionalFieldOf("gain_handler")
                            .forGetter(o -> Optional.ofNullable(o.gainHandler())),
                    TRIGGER_CODEC.listOf().optionalFieldOf("triggers")
                            .forGetter(o -> Optional.ofNullable(o.triggers())),
                    SHARE_CODEC.listOf().optionalFieldOf("shares")
                            .forGetter(o -> Optional.ofNullable(o.shares())),
                    EFFECT_CODEC.listOf().optionalFieldOf("effects")
                            .forGetter(o -> Optional.ofNullable(o.effects()))
            ).apply(instance, MasterySpecOverlay::create));

    /** The overlay codec (REQ-CORE-12); see docs/datapack-overrides.md. */
    public static final Codec<MasterySpecOverlay> CODEC = RECORD_CODEC;

    public MasterySpecOverlay {
        if (baseGain != null && (!Double.isFinite(baseGain) || baseGain < 0.0)) {
            throw new IllegalArgumentException(
                    "overlay base_gain must be finite and >= 0, got " + baseGain);
        }
        if (gainHandler != null && gainHandler.isBlank()) {
            throw new IllegalArgumentException("overlay gain_handler must not be blank");
        }
        if (triggers != null) {
            triggers = List.copyOf(triggers);
        }
        if (shares != null) {
            shares = List.copyOf(shares);
            double sum = 0.0;
            for (Share share : shares) {
                if (share == null) {
                    throw new IllegalArgumentException("overlay shares must not contain null");
                }
                sum += share.share();
            }
            if (sum > 1.0 + 1.0e-9) {
                throw new IllegalArgumentException(
                        "overlay share sum must be <= 1 (self keeps the remainder), got " + sum);
            }
        }
        if (effects != null) {
            effects = List.copyOf(effects);
            Set<MasteryAxis> seen = java.util.EnumSet.noneOf(MasteryAxis.class);
            for (EffectEntry entry : effects) {
                if (entry == null) {
                    throw new IllegalArgumentException("overlay effects must not contain null");
                }
                if (!seen.add(entry.axis())) {
                    throw new IllegalArgumentException(
                            "overlay effects list axis duplicated: " + entry.axis());
                }
            }
        }
    }

    @SuppressWarnings("unused") // adapter for optional codec fields -> record
    private static MasterySpecOverlay create(
            Optional<Double> baseGain,
            Optional<String> gainHandler,
            Optional<List<MasteryTrigger>> triggers,
            Optional<List<Share>> shares,
            Optional<List<EffectEntry>> effects) {
        return new MasterySpecOverlay(
                baseGain.orElse(null), gainHandler.orElse(null), triggers.orElse(null),
                shares.orElse(null), effects.orElse(null));
    }

    /**
     * Applies this overlay to the code-declared base at lookup (the same
     * present-replaces/absent-keeps convention as definition overlays;
     * lists replace wholesale - a share list or effect set is a complete
     * statement, never a union).
     *
     * @param base the declared spec (never null - callers gate on it)
     * @return the merged spec, validated by {@link MasterySpec}'s constructor
     */
    public MasterySpec mergeInto(MasterySpec base) {
        Map<MasteryAxis, MasterySpec.EffectRule> mergedEffects =
                new EnumMap<>(MasteryAxis.class);
        if (effects != null) {
            for (EffectEntry entry : effects) {
                mergedEffects.put(entry.axis(), entry.toRule());
            }
        }
        return new MasterySpec(
                baseGain != null ? baseGain : base.baseGain(),
                triggers != null ? Set.copyOf(triggers) : base.triggers(),
                shares != null
                        ? shares.stream().map(Share::toSpecShare).toList()
                        : base.shares(),
                gainHandler != null ? gainHandler : base.gainHandler(),
                effects != null ? java.util.Collections.unmodifiableMap(mergedEffects)
                        : base.effects());
    }
}
