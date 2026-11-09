package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Menu visibility contract (core#124): the shipped defaults answer, a
 * declared handler owns its id, undeclared ids stay visible (never-guess),
 * conflicts fail loud, nulls fail closed.
 */
class VisibilityHandlersTest {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "visible_probe");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath("tensura", "visible_probe_other");

    @AfterEach
    void tearDown() {
        VisibilityHandlers.remove(ID);
        VisibilityHandlers.remove(OTHER);
    }

    @Test
    void shippedDefaultsAnswer() {
        assertTrue(VisibilityHandlers.ALWAYS.visible(null, ID), "ALWAYS lists");
        assertFalse(VisibilityHandlers.NEVER.visible(null, ID), "NEVER hides");
    }

    @Test
    void declaredHandlerOwnsItsId() {
        String[] seen = new String[1];
        VisibilityHandlers.declare(ID, (player, abilityId) -> {
            seen[0] = abilityId.toString();
            return false;
        });

        assertFalse(VisibilityHandlers.visible(null, ID), "the declared handler answers");
        assertEquals(ID.toString(), seen[0], "the handler sees the id asked about");
        assertTrue(VisibilityHandlers.has(ID));
    }

    @Test
    void undeclaredStaysVisible() {
        assertTrue(VisibilityHandlers.visible(null, OTHER),
                "an undeclared id must keep listing - unknown never hides");
        assertTrue(VisibilityHandlers.visibleWhenUndeclared(
                        org.bitwisemadness.abilitycore.config.CoreConfig.UndeclaredVisibility.VISIBLE),
                "the shipped rule lists");
        assertFalse(VisibilityHandlers.visibleWhenUndeclared(
                        org.bitwisemadness.abilitycore.config.CoreConfig.UndeclaredVisibility.HIDDEN),
                "a pack may opt into hiding undeclared ids");
    }

    @Test
    void duplicateDeclarationsFailLoud() {
        VisibilityHandlers.declare(ID, VisibilityHandlers.ALWAYS);
        assertThrows(IllegalStateException.class,
                () -> VisibilityHandlers.declare(ID, VisibilityHandlers.NEVER),
                "two handlers claiming one ability must fail");
    }

    @Test
    void nullsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> VisibilityHandlers.declare(null, VisibilityHandlers.ALWAYS));
        assertThrows(IllegalArgumentException.class,
                () -> VisibilityHandlers.declare(ID, null));
        assertFalse(VisibilityHandlers.visible(null, null),
                "a null id is malformed input - nothing to list");
    }

    @Test
    void removeRestoresTheDefault() {
        VisibilityHandlers.declare(ID, VisibilityHandlers.NEVER);
        assertFalse(VisibilityHandlers.visible(null, ID));

        assertTrue(VisibilityHandlers.remove(ID));
        assertFalse(VisibilityHandlers.remove(ID), "second removal is a no-op");
        assertTrue(VisibilityHandlers.visible(null, ID), "undeclared is visible again");
    }
}
