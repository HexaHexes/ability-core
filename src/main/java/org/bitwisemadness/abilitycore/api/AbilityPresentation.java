package org.bitwisemadness.abilitycore.api;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * What a provider presents for one ability in the menu (core#122): an
 * optional toggle state plus ordered live fields - the whole contribution
 * the consumer renders beyond the ability's static schema (#119's
 * stats/description).
 *
 * <p>A provider that has nothing to add answers no presentation at all
 * ({@code Optional.empty()} at the registry) rather than an empty object -
 * unknown is never a guess (REQ-HOT-08 discipline).</p>
 *
 * @param toggle the current toggle state, or null when the ability is not
 *               togglable (null = no Enable/Disable control, never "off")
 * @param fields the live fields in presentation order, never null
 */
public record AbilityPresentation(@Nullable Toggle toggle, List<PresentationField> fields) {

    public AbilityPresentation {
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    }
}
