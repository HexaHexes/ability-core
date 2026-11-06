package org.bitwisemadness.abilitycore.api;

import java.util.Objects;

/**
 * One provider-presented field for the menu (core#122): an ordered, live
 * fact a provider contributes for its ability - a label with either display
 * text or a 0..1 progress meter (the experience-bar shape).
 *
 * <p>Labels are provider-owned translation keys (the {@code displayNameKey}
 * rule: core invents no player strings; consumers localise with their own
 * fallback). Values are display text and never parsed into anything
 * authoritative; declaration order is presentation order.</p>
 *
 * @param labelKey translation key for the field's name
 * @param kind     how the menu renders it: plain text or a progress meter
 * @param value    the display text (blank = label only, e.g. a meter without a count)
 * @param progress the 0..1 fraction for {@link Kind#PROGRESS} (validated for
 *                 every kind so a malformed field can never reach a meter)
 */
public record PresentationField(String labelKey, Kind kind, String value, float progress) {

    /** How the menu renders the field. */
    public enum Kind {
        /** {@code label: value} text. */
        TEXT,
        /** A 0..1 progress meter (plus {@code value} when non-blank). */
        PROGRESS
    }

    public PresentationField {
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        if (labelKey.isBlank()) {
            throw new IllegalArgumentException("presentation field label key must not be blank");
        }
        if (!Float.isFinite(progress) || progress < 0f || progress > 1f) {
            throw new IllegalArgumentException(
                    "progress must be finite and in [0,1], got " + progress);
        }
    }

    /** A text field: label plus display value (blank = label only). */
    public static PresentationField text(String labelKey, String value) {
        return new PresentationField(labelKey, Kind.TEXT, value, 0f);
    }

    /** A progress field: label plus 0..1 fraction plus optional value text. */
    public static PresentationField progress(String labelKey, float fraction, String valueText) {
        return new PresentationField(labelKey, Kind.PROGRESS, valueText, fraction);
    }
}
