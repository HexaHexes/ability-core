package org.bitwisemadness.abilitycore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Core's reference SERVER + CLIENT config pair (issue #9 AC2): both specs
 * exist with parseable defaults, each key sits in its own side's spec, and
 * the live read normalizes the not-loaded state through the diagnostics
 * helper instead of throwing.
 */
class CoreConfigTest {

    @Test
    void serverSpecCarriesTheRefusalLogKeyWithItsDefault() {
        assertFalse(CoreConfig.SERVER_SPEC.isEmpty(), "the SERVER spec must define keys");
        assertEquals(List.of("diagnostics", "refusalLog"), CoreConfig.REFUSAL_LOG.getPath());
        assertEquals(CoreConfig.RefusalLogMode.ONCE_PER_KEY, CoreConfig.REFUSAL_LOG.getDefault());
        assertTrue(CoreConfig.SERVER_SPEC.getValues().contains("diagnostics.refusalLog"),
                "the SERVER spec must expose its key");
        assertFalse(CoreConfig.SERVER_SPEC.getValues().contains("diagnostics.playerNotices"),
                "the client key must never live in the SERVER spec");
    }

    @Test
    void clientSpecCarriesTheNoticeKeyWithItsDefault() {
        assertFalse(CoreConfig.CLIENT_SPEC.isEmpty(), "the CLIENT spec must define keys");
        assertEquals(List.of("diagnostics", "playerNotices"), CoreConfig.PLAYER_NOTICES.getPath());
        assertEquals(Boolean.TRUE, CoreConfig.PLAYER_NOTICES.getDefault());
        assertTrue(CoreConfig.CLIENT_SPEC.getValues().contains("diagnostics.playerNotices"),
                "the CLIENT spec must expose its key");
        assertFalse(CoreConfig.CLIENT_SPEC.getValues().contains("diagnostics.refusalLog"),
                "the server key must never live in the CLIENT spec");
    }

    @Test
    void liveReadNormalizesTheNotLoadedStateToTheDefault() {
        // Unit tests load no config file: the read must arrive at the spec
        // default through ConfigDiagnostics, never throw (AC3/AC4 wiring).
        assertEquals(CoreConfig.RefusalLogMode.ONCE_PER_KEY, CoreConfig.refusalLogMode());
    }

    @Test
    void everyAuditKeyLivesInTheServerSpecAndNeverTheClientOne() {
        for (String key : List.of(
                CoreConfig.HEALTH_COSTS_CAN_KILL_KEY,
                CoreConfig.DISABLED_RESOURCES_KEY,
                CoreConfig.INTEGRAL_COST_ROUNDING_KEY,
                CoreConfig.PERSIST_EXTERNAL_COOLDOWNS_KEY,
                CoreConfig.UNDECLARED_ABILITIES_KEY,
                CoreConfig.MAX_AREA_RADIUS_KEY,
                CoreConfig.ACTIVATION_RATE_KEY,
                CoreConfig.MALFORMED_PAYLOAD_LOG_KEY)) {
            assertTrue(CoreConfig.SERVER_SPEC.getValues().contains(key),
                    "the SERVER spec must expose " + key);
            assertFalse(CoreConfig.CLIENT_SPEC.getValues().contains(key),
                    "policy never lives in the CLIENT spec: " + key);
        }
    }

    @Test
    void auditReadsNormalizeTheNotLoadedStateToTheirDefaults() {
        assertTrue(CoreConfig.healthCostsCanKill(), "lethality defaults on");
        assertTrue(CoreConfig.disabledResources().isEmpty(), "no currency banned by default");
        assertFalse(CoreConfig.resourceDisabled(CoreConfig.DISABLED_RESOURCES_KEY),
                "an unbanned id reads unbanned");
        assertEquals(CoreConfig.RoundingMode.CEIL, CoreConfig.integralCostRounding());
        assertFalse(CoreConfig.persistExternalCooldowns(), "persistence defaults off");
        assertEquals(CoreConfig.UndeclaredVisibility.VISIBLE, CoreConfig.undeclaredAbilities());
        assertEquals(64.0, CoreConfig.maxAreaRadius());
        assertEquals(0, CoreConfig.activationRatePerSecond(), "0 = unlimited by default");
        assertEquals(CoreConfig.RefusalLogMode.ONCE_PER_KEY, CoreConfig.malformedPayloadLog());
    }
}
