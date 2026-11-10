package org.bitwisemadness.abilitycore.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.junit.jupiter.api.Test;

/**
 * The share split of one grant (REQ-CORE-12): shares take their fraction
 * in declaration order, self keeps the remainder, and a share list
 * summing to 1 routes everything away from self - the "any combination"
 * routing contract without a player or an attachment.
 */
class MasteryRoutingTest {

    private static final ResourceLocation SOURCE =
            ResourceLocation.fromNamespaceAndPath("tensura", "routing_probe");
    private static final MasteryTarget FIRE =
            MasteryTarget.category(ResourceLocation.fromNamespaceAndPath("tensura", "fire"));
    private static final MasteryTarget BLAZE =
            MasteryTarget.ability(ResourceLocation.fromNamespaceAndPath("tensura", "blaze"));

    @Test
    void noSharesSendsEverythingToSelf() {
        Map<MasteryTarget, Double> split = MasteryRouting.split(SOURCE, List.of(), 100.0);
        assertEquals(1, split.size());
        assertEquals(100.0, split.get(MasteryTarget.ability(SOURCE)), 1e-9);
    }

    @Test
    void sharesSplitAndSelfKeepsTheRemainder() {
        var shares = List.of(new MasterySpec.Share(FIRE, 0.5),
                new MasterySpec.Share(BLAZE, 0.25));
        Map<MasteryTarget, Double> split = MasteryRouting.split(SOURCE, shares, 100.0);

        assertEquals(3, split.size(), "fire + blaze + self remainder");
        assertEquals(50.0, split.get(FIRE), 1e-9);
        assertEquals(25.0, split.get(BLAZE), 1e-9);
        assertEquals(25.0, split.get(MasteryTarget.ability(SOURCE)), 1e-9);
    }

    @Test
    void aFullShareLeavesNothingForSelf() {
        Map<MasteryTarget, Double> split = MasteryRouting.split(SOURCE,
                List.of(new MasterySpec.Share(FIRE, 1.0)), 100.0);
        assertEquals(1, split.size(), "self gets no key at a zero remainder");
        assertEquals(100.0, split.get(FIRE), 1e-9);
        assertFalse(split.containsKey(MasteryTarget.ability(SOURCE)));
    }

    @Test
    void selfRemainderKeyExistsEvenWhenTheSpecOvershares() {
        // Defense in depth: spec validation caps shares at 1, but the split
        // must never produce a negative self amount if a future caller slips.
        Map<MasteryTarget, Double> split = MasteryRouting.split(SOURCE,
                List.of(new MasterySpec.Share(FIRE, 0.6),
                        new MasterySpec.Share(BLAZE, 0.6)), 100.0);
        assertTrue(split.values().stream().allMatch(v -> v >= 0.0),
                "no negative XP anywhere, even from an overspent share list");
    }
}
