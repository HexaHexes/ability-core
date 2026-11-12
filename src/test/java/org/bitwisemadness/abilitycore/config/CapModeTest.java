package org.bitwisemadness.abilitycore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The per-integration cap-mode switch (REQ-CORE-12): an exact
 * namespace entry wins, the {@code *} wildcard sets the default, and an
 * unmatched namespace (or an empty list) falls back to SOFT - the
 * shipped default. Pure so fixtures pin it without a config.
 */
class CapModeTest {

    private static final ResourceLocation ISS =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fireball");
    private static final ResourceLocation TENSURA =
            ResourceLocation.fromNamespaceAndPath("tensura", "battlewill");

    @Test
    void wildcardSetsTheDefault() {
        assertEquals(CoreConfig.CapMode.SOFT,
                CoreConfig.capModeFrom(List.of("*=soft"), ISS.getNamespace()));
        assertEquals(CoreConfig.CapMode.HARD,
                CoreConfig.capModeFrom(List.of("*=hard"), TENSURA.getNamespace()),
                "a hard default applies to every namespace");
    }

    @Test
    void exactNamespaceBeatsTheWildcard() {
        List<String> entries = List.of("*=soft", "irons_spellbooks=hard");
        assertEquals(CoreConfig.CapMode.HARD,
                CoreConfig.capModeFrom(entries, ISS.getNamespace()),
                "the integration's own entry wins");
        assertEquals(CoreConfig.CapMode.SOFT,
                CoreConfig.capModeFrom(entries, TENSURA.getNamespace()));
    }

    @Test
    void unmatchedNamespacesFallBackToSoft() {
        assertEquals(CoreConfig.CapMode.SOFT,
                CoreConfig.capModeFrom(List.of("other=hard"), ISS.getNamespace()),
                "an unmatched namespace takes the safe default");
        assertEquals(CoreConfig.CapMode.SOFT,
                CoreConfig.capModeFrom(List.of(), ISS.getNamespace()),
                "an empty list is the shipped SOFT default");
    }
}
