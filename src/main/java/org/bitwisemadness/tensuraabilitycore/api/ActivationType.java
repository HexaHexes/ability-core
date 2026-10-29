package org.bitwisemadness.tensuraabilitycore.api;

import com.mojang.serialization.Codec;

/**
 * How an ability is activated, from the shared-contract perspective.
 *
 * <p>The vocabulary is deliberately small and provider-independent: providers
 * map their native activation shapes onto it. {@link #UNKNOWN} is the
 * fail-closed value - an ability whose activation type cannot be determined is
 * never treated as activatable (REQ-CORE-02/07 semantics).</p>
 */
public enum ActivationType {

    /** Fires immediately on activation (vanilla-attack-like instant effects). */
    INSTANT,
    /** Requires a charge-up before taking effect. */
    CHARGED,
    /** Runs over time while held/channeled. */
    CHANNELLED,
    /** Toggled on/off; may tick while active. */
    TOGGLE,
    /** Unrecognised or undetermined - refused at activation, never guessed. */
    UNKNOWN;

    /**
     * Strict mapping for datapack/config decoding: unknown values are rejected
     * loudly at parse time (datapack layer rule: invalid values produce
     * diagnostics, they never silently apply).
     */
    public static final Codec<ActivationType> CODEC = Codec.STRING.xmap(
            s -> {
                ActivationType type = fromWire(s);
                if (type == UNKNOWN && !"unknown".equalsIgnoreCase(s)) {
                    throw new IllegalArgumentException("Unknown activation type: " + s
                            + " (valid: instant, charged, channelled, toggle)");
                }
                return type;
            },
            ActivationType::wireName);

    /**
     * Fail-closed mapping for runtime/network values: anything unrecognised
     * becomes {@link #UNKNOWN} instead of throwing (REQ-CORE-03/07: unknown
     * means unavailable, not "try anyway").
     */
    public static ActivationType fromWire(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "instant" -> INSTANT;
            case "charged" -> CHARGED;
            case "channelled", "continuous" -> CHANNELLED;
            case "toggle" -> TOGGLE;
            case "unknown" -> UNKNOWN;
            default -> UNKNOWN;
        };
    }

    public String wireName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
