package org.bitwisemadness.abilitycore.api;

/**
 * Where an activation attempt originates (REQ-CORE-02).
 *
 * <p>Source is part of the server-authoritative policy surface: eligibility
 * callbacks may scope rules per source (e.g. combo-chain whitelist/blacklist rules
 * apply only to {@link #COMBO} - the brief's "filters affect combo activation
 * only" rule made structural, REQ-CORE-07).</p>
 */
public enum ActivationSource {

    /** Direct slot hotkey (Ctrl+1..9 style). */
    DIRECT_SLOT,
    /** Selected-slot activation key (G-style). */
    SELECTED_SLOT,
    /** Combo chain via a combo-chain integration. */
    COMBO,
    /** Ability management menu action. */
    MENU,
    /** Another mod's adapter calling through the shared contract. */
    NATIVE_ADAPTER;

    /**
     * Fail-closed wire mapping: an unrecognised source refuses the request
     * (mapped to {@code null}; the pipeline answers REFUSED_UNAVAILABLE) -
     * never a guess, never a default that could widen policy.
     */
    public static ActivationSource fromWire(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "direct_slot", "direct" -> DIRECT_SLOT;
            case "selected_slot", "selected" -> SELECTED_SLOT;
            case "combo" -> COMBO;
            case "menu" -> MENU;
            case "native_adapter", "adapter" -> NATIVE_ADAPTER;
            default -> null;
        };
    }

    public String wireName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
