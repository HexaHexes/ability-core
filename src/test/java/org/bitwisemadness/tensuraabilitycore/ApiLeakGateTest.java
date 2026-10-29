package org.bitwisemadness.tensuraabilitycore;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.junit.jupiter.api.Test;

/**
 * CI leak gate (REQ-CORE-01 / REQ-XREF-03): the shared contract packages must
 * never reference upstream mod types. Upstream knowledge belongs exclusively in
 * the consumer repos' provider adapters (ADR-005).
 *
 * <p>Mechanism: scan the compiled class files of the {@code api} and
 * {@code registry} packages for the forbidden internal name prefixes in their
 * constant pools. Upstream classes would appear there if any signature,
 * field, or instruction referenced them.</p>
 */
class ApiLeakGateTest {

    private static final String[] FORBIDDEN = {
            "io/redspace/ironsspellbooks/",
            "io/github/manasmods/",
            "net/bettercombat/",
            "net/kayn/better_spellcasting/",
    };

    @Test
    void contractPackagesReferenceNoUpstreamModTypes() throws Exception {
        Path mainClasses = Path.of(AbilityDefinition.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainClasses)) {
            for (Path classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                String relative = mainClasses.relativize(classFile).toString().replace('\\', '/');
                boolean inContractPackages = relative.startsWith("org/bitwisemadness/tensuraabilitycore/api/")
                        || relative.startsWith("org/bitwisemadness/tensuraabilitycore/registry/");
                if (!inContractPackages) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(classFile);
                for (String needle : FORBIDDEN) {
                    if (containsAscii(bytes, needle)) {
                        violations.add(relative + " references " + needle);
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Upstream types leaked into the shared contract packages:\n"
                    + String.join("\n", violations)
                    + "\nUpstream knowledge belongs in consumer-side provider adapters (ADR-005).");
        }
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
