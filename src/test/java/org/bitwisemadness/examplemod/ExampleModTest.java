package org.bitwisemadness.examplemod;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Placeholder JUnit suite. The CI {@code junit} check asserts a non-zero test
 * count so it cannot pass vacuously, so every repo derived from this template
 * keeps at least this file (and adds real unit tests as logic grows).
 */
class ExampleModTest {

    @Test
    void modIdIsALowercasePathSegment() {
        // MOD_ID is a compile-time constant, so referencing it does not trigger
        // class initialization - this test needs no Minecraft runtime.
        assertTrue(ExampleMod.MOD_ID.matches("[a-z][a-z0-9_]{2,63}"),
                "mod id must be a lowercase resource location path segment");
    }
}
