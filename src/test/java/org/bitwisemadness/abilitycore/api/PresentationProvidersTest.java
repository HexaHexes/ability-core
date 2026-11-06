package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The presentation contract (core#122): loud registry ownership, the
 * never-guess lookup (undeclared and null-degrading providers answer
 * "render the base row"), field/toggle validation, and presentation order.
 */
class PresentationProvidersTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");

    @AfterEach
    void isolate() {
        PresentationProviders.remove(ABILITY);
        PresentationProviders.remove(OTHER);
    }

    @Test
    void declarationIsLoud() {
        assertThrows(IllegalArgumentException.class,
                () -> PresentationProviders.declare(null, (player, id) -> null));
        assertThrows(IllegalArgumentException.class,
                () -> PresentationProviders.declare(ABILITY, null));

        PresentationProviders.declare(ABILITY, (player, id) -> new AbilityPresentation(null, List.of()));
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> PresentationProviders.declare(ABILITY, (player, id) -> new AbilityPresentation(null, List.of())));
        assertTrue(duplicate.getMessage().contains(ABILITY.toString()), duplicate.getMessage());
        assertTrue(PresentationProviders.has(ABILITY));
        assertTrue(PresentationProviders.remove(ABILITY));
        assertFalse(PresentationProviders.has(ABILITY));
    }

    @Test
    void undeclaredAndNullAnswersNeverGuess() {
        // empty = "render the base row" - unknown is never a wrong value
        assertTrue(PresentationProviders.present(null, ABILITY).isEmpty(),
                "undeclared id answers no presentation");
        assertTrue(PresentationProviders.present(null, null).isEmpty(),
                "a null id answers no presentation");

        PresentationProviders.declare(ABILITY, (player, id) -> null); // contract violation
        assertTrue(PresentationProviders.present(null, ABILITY).isEmpty(),
                "a null-returning provider degrades to no presentation, never a crash");
    }

    @Test
    void presentReturnsTheProvidersContribution() {
        var presentation = new AbilityPresentation(new Toggle(true),
                List.of(PresentationField.progress("tensura.menu.field.experience", 0.25f, "12/50"),
                        PresentationField.text("tensura.menu.field.kind", "Fire magic")));
        PresentationProviders.declare(ABILITY, (player, id) -> presentation);

        var answered = PresentationProviders.present(null, ABILITY).orElseThrow();
        assertEquals(new Toggle(true), answered.toggle(), "the toggle state round-trips");
        assertEquals("12/50", answered.fields().get(0).value());
        assertEquals(PresentationField.Kind.PROGRESS, answered.fields().get(0).kind());
        assertEquals(0.25f, answered.fields().get(0).progress());
        assertEquals(PresentationField.Kind.TEXT, answered.fields().get(1).kind());
    }

    @Test
    void presentationFieldsValidateLoudly() {
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationField(" ", PresentationField.Kind.TEXT, "v", 0f));
        assertThrows(NullPointerException.class,
                () -> new PresentationField(null, PresentationField.Kind.TEXT, "v", 0f));
        assertThrows(NullPointerException.class,
                () -> new PresentationField("k", PresentationField.Kind.TEXT, null, 0f));
        assertThrows(NullPointerException.class,
                () -> new PresentationField("k", null, "v", 0f));
        assertThrows(IllegalArgumentException.class,
                () -> PresentationField.progress("k", 1.5f, ""),
                "progress past 1 can never reach a meter");
        assertThrows(IllegalArgumentException.class,
                () -> PresentationField.progress("k", Float.NaN, ""));
        assertThrows(IllegalArgumentException.class,
                () -> PresentationField.progress("k", -0.1f, ""),
                "negative progress can never reach a meter");
    }

    @Test
    void fieldsAndToggleHoldTheirContracts() {
        // presentation order = declaration order; toggle null means "not
        // togglable", never "off"
        var fields = List.of(PresentationField.text("a.b", "1"), PresentationField.text("a.c", "2"));
        var presentation = new AbilityPresentation(null, fields);
        assertNull(presentation.toggle());
        assertEquals(fields, presentation.fields());
        assertThrows(UnsupportedOperationException.class, () -> presentation.fields().add(
                PresentationField.text("a.d", "3")));
        assertThrows(NullPointerException.class, () -> new AbilityPresentation(null, null));
        assertFalse(new Toggle(false).enabled());
        assertTrue(new Toggle(true).enabled());
    }
}
