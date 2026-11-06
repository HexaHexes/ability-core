package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The refund dispatch (issue #107): the owner's inverse runs with the exact
 * charged snapshot; absent/non-refundable/throwing paths answer false with a
 * loud log and nothing thrown at the player; misuse stays loud.
 */
class ResourceConsumersRefundTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "refund_probe");
    private static final String REFUNDABLE = "test:refundable";
    private static final String PLAIN = "test:plain";
    private static final String THROWING = "test:throwing";

    private Affordability recorded;
    private ResourceLocation recordedAbility;

    @AfterEach
    void clean() {
        for (String id : new String[] {REFUNDABLE, PLAIN, THROWING}) {
            ResourceConsumers.remove(id);
            ResourceProviders.remove(id);
        }
        recorded = null;
        recordedAbility = null;
    }

    private void declarePair(String id, ResourceConsumer consumer) {
        ResourceProviders.declare(id, (abilityId, player, amount) ->
                Affordability.sufficient(id, amount));
        ResourceConsumers.declare(id, consumer);
    }

    @Test
    void refundPassesTheExactChargedSnapshotToTheOwner() {
        declarePair(REFUNDABLE, new RefundableConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                // the pipeline's charge site is covered elsewhere; dispatch is the unit here
            }

            @Override
            public void refund(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                recordedAbility = abilityId;
                recorded = snapshot;
            }
        });
        var charged = Affordability.sufficient(REFUNDABLE, 7.0);

        assertTrue(ResourceConsumers.refund(ABILITY, null, charged),
                "the owner's inverse ran");
        assertSame(charged, recorded,
                "the exact charged snapshot - never a recomputed amount (charge/refund cannot drift)");
        assertSame(ABILITY, recordedAbility, "the ability id travels unchanged");
    }

    @Test
    void nonRefundableConsumersAnswerFalseWithALoudLog() {
        declarePair(PLAIN, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                // a legal, non-refundable consumer
            }
        });

        boolean refunded;
        try (var logs = LogCapture.forLogger(ResourceConsumers.class, Level.ERROR)) {
            refunded = ResourceConsumers.refund(ABILITY, null, Affordability.sufficient(PLAIN, 1.0));
            assertFalse(refunded, "not refundable -> false, never an exception");
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains(PLAIN)
                            && m.contains("not refundable")),
                    "loud log names the resource and the reason: " + logs.messages());
        }
    }

    @Test
    void absentConsumerAnswersFalse() {
        boolean refunded;
        try (var logs = LogCapture.forLogger(ResourceConsumers.class, Level.ERROR)) {
            refunded = ResourceConsumers.refund(ABILITY, null,
                    Affordability.sufficient("test:never_declared", 1.0));
            assertFalse(refunded);
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains("test:never_declared")),
                    "loud log names the resource: " + logs.messages());
        }
    }

    @Test
    void throwingRefundIsCaughtAndLogged() {
        declarePair(THROWING, new RefundableConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
            }

            @Override
            public void refund(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                throw new IllegalStateException("refund backend down");
            }
        });

        boolean refunded;
        try (var logs = LogCapture.forLogger(ResourceConsumers.class, Level.ERROR)) {
            refunded = ResourceConsumers.refund(ABILITY, null, Affordability.sufficient(THROWING, 1.0));
            assertFalse(refunded, "a throwing inverse is caught - never propagates");
            assertTrue(logs.messages().stream().anyMatch(m -> m.contains(THROWING)),
                    "the error names the resource: " + logs.messages());
        }
    }

    @Test
    void programmerMisuseIsLoud() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourceConsumers.refund(null, null, Affordability.sufficient(PLAIN, 1.0)),
                "a null ability id is programmer misuse");
        assertThrows(IllegalArgumentException.class,
                () -> ResourceConsumers.refund(ABILITY, null, null),
                "refund takes the exact charged snapshot, not null");
    }
}
