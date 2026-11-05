package org.bitwisemadness.abilitycore;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.junit.jupiter.api.Test;

/**
 * CI leak gate (REQ-CORE-01 / REQ-XREF-03): the shared contract packages must
 * never reference upstream mod types. Upstream knowledge belongs exclusively in
 * the consumer repos' provider adapters (ADR-005).
 *
 * <p>Mechanism: scan the compiled class files of the {@code api},
 * {@code registry} and {@code discovery} packages for the forbidden internal
 * name prefixes in their constant pools. Upstream classes would appear there
 * if any signature, field, or instruction referenced them.</p>
 *
 * <p>{@code ModList} is scoped separately (core#92): the core#14 AC - "discovery
 * must never branch on ModList presence; provider presence IS registration
 * presence" - binds {@code discovery} (and {@code registry}, which has no use
 * case), while {@code api} may use it for <b>declaration-time</b> optional
 * gates ({@code declare(..., requiredModId)}) - a setup-time loader check,
 * never a runtime presence inference about registration.</p>
 */
class ApiLeakGateTest {

    /** Upstream mod internals - forbidden in every contract package. */
    private static final String[] UPSTREAM_FORBIDDEN = {
            "io/redspace/ironsspellbooks/",
            "io/github/manasmods/",
            "net/bettercombat/",
            "net/kayn/better_spellcasting/",
    };

    /** ModList - forbidden where the core#14 AC applies (see class javadoc). */
    private static final String[] MODLIST_NEEDLE = {"net/neoforged/fml/ModList"};

    private static final String[] SCANNED_PACKAGES = {
            "org/bitwisemadness/abilitycore/api/",
            "org/bitwisemadness/abilitycore/registry/",
            "org/bitwisemadness/abilitycore/discovery/",
    };

    private static final String[] MODLIST_FORBIDDEN_PACKAGES = {
            "org/bitwisemadness/abilitycore/registry/",
            "org/bitwisemadness/abilitycore/discovery/",
    };

    @Test
    void contractPackagesReferenceNoUpstreamModTypes() throws Exception {
        Path mainClasses = Path.of(AbilityDefinition.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainClasses)) {
            for (Path classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                String relative = mainClasses.relativize(classFile).toString().replace('\\', '/');
                String contractPackage = contractPackageOf(relative);
                if (contractPackage == null) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(classFile);
                for (String needle : UPSTREAM_FORBIDDEN) {
                    if (containsAscii(bytes, needle)) {
                        violations.add(relative + " references " + needle);
                    }
                }
                if (isModListForbidden(contractPackage) && containsAscii(bytes, MODLIST_NEEDLE[0])) {
                    violations.add(relative + " references ModList - the core#14 AC forbids presence"
                            + " inference in " + contractPackage + " (api may gate declarations, see core#92)");
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Upstream types leaked into the shared contract packages:\n"
                    + String.join("\n", violations)
                    + "\nUpstream knowledge belongs in consumer-side provider adapters (ADR-005).");
        }
    }

    /** The contract package this class file belongs to, or null if not scanned. */
    private static String contractPackageOf(String relative) {
        for (String prefix : SCANNED_PACKAGES) {
            if (relative.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    private static boolean isModListForbidden(String contractPackage) {
        for (String prefix : MODLIST_FORBIDDEN_PACKAGES) {
            if (contractPackage.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] need = needle.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - need.length; i++) {
            for (int j = 0; j < need.length; j++) {
                if (haystack[i + j] != need[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
