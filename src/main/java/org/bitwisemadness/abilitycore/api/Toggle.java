package org.bitwisemadness.abilitycore.api;

/**
 * A togglable ability's current state (core#122): presenting this means the
 * ability is togglable and the consumer may show an Enable/Disable control.
 *
 * <p>The control executes through the <b>existing activation pipeline</b> -
 * {@code ActivateAbilityPayload} with {@link ActivationSource#MENU} and
 * {@link ActivationRequest#NO_SLOT}: no new intent type exists for it, every
 * eligibility gate stays the authority, and a TOGGLE-typed ability flips in
 * its provider's handler. Display reads are provider-owned live state (the
 * #117 client-readable posture); core stores nothing. This exact shape also
 * marks the mastery award's one skip (REQ-CORE-12): flipping a switch is not
 * activating the ability, so the pipeline's post-activation grant does not
 * fire for it - otherwise toggle-spam would farm mastery XP.</p>
 *
 * @param enabled the ability's current toggle state
 */
public record Toggle(boolean enabled) {
}
