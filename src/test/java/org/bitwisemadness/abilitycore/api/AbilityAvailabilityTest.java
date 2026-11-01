package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Overlay data contract (REQ-HOT-08): the option-typed tuple keeps a synthetic
 * no-metric provider honest (unknown, never a wrong value), a real provider
 * populates it, a missing provider yields no tuple (no overlay, no crash), and
 * a buggy null-returning provider degrades safely.
 */
class AbilityAvailabilityTest {

    private static final ResourceLocation FIREBALL =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");
    private static final ResourceLocation HEAL =
            ResourceLocation.fromNamespaceAndPath("tensura", "heal");

    @AfterEach
    void tearDown() {
        AvailabilityProviders.remove(FIREBALL);
        AvailabilityProviders.remove(HEAL);
    }

    @Test
    void syntheticNoMetricProviderRendersUnknownWithoutLying() {
        // A provider that exposes nothing reports every metric unknown - the
        // overlay renders no overlay rather than a wrong cooldown/cost/verdict.
        AbilityAvailability none = AbilityAvailability.allUnknown();

        assertFalse(none.cooldown().isKnown(), "cooldown must read unknown, not 0");
        assertFalse(none.cost().isKnown(), "cost must read unknown, not zero");
        assertFalse(none.availability().isAvailable(), "unknown must not read as available");
        assertEquals(Optional.empty(), none.cooldown().knownValue());
        assertEquals(Optional.empty(), none.cost().knownValue());
    }

    @Test
    void realProviderPopulatesEveryMetric() {
        AvailabilityProviders.declare(FIREBALL, (player, id) -> new AbilityAvailability(
                Metric.known(0.5),
                Metric.known(new ResourceCost("tensura:aura", 20.0)),
                AvailabilityState.unavailable("cooldown")));

        Optional<AbilityAvailability> result = AvailabilityProviders.availabilityFor(null, FIREBALL);

        assertTrue(result.isPresent());
        AbilityAvailability tuple = result.get();
        assertEquals(Optional.of(0.5), tuple.cooldown().knownValue());
        assertTrue(tuple.cost().isKnown());
        assertEquals("tensura:aura", tuple.cost().knownValue().orElseThrow().resourceId());
        assertFalse(tuple.isAvailable(), "unavailable verdict must not read as available");
        assertEquals("cooldown",
                ((AvailabilityState.Unavailable) tuple.availability()).reason());
    }

    @Test
    void zeroCooldownMeansReadyWhileUnknownMeansCannotTell() {
        // The fail-safe distinction: a real 0 is 'ready', unknown is 'no overlay'.
        AbilityAvailability ready = new AbilityAvailability(
                Metric.known(0.0), Metric.unknown(), AvailabilityState.available());
        assertTrue(ready.cooldown().isKnown());
        assertEquals(Optional.of(0.0), ready.cooldown().knownValue());
        assertTrue(ready.isAvailable());

        AbilityAvailability unclear = AbilityAvailability.allUnknown();
        assertFalse(unclear.cooldown().isKnown());
        assertFalse(unclear.isAvailable());
    }

    @Test
    void missingProviderYieldsNoTupleAndNoCrash() {
        // No provider declared -> empty (render no overlay), never an exception.
        Optional<AbilityAvailability> result = AvailabilityProviders.availabilityFor(null, FIREBALL);
        assertFalse(result.isPresent());
        assertFalse(AvailabilityProviders.has(FIREBALL));
    }

    @Test
    void nullReturningProviderDegradesToAllUnknownInsteadOfCrashing() {
        AvailabilityProviders.declare(HEAL, (player, id) -> null);

        Optional<AbilityAvailability> result = AvailabilityProviders.availabilityFor(null, HEAL);

        assertTrue(result.isPresent(), "a buggy provider must not crash the overlay");
        AbilityAvailability degraded = result.get();
        assertFalse(degraded.cooldown().isKnown());
        assertFalse(degraded.cost().isKnown());
        assertFalse(degraded.availability().isAvailable());
    }

    @Test
    void cooldownOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityAvailability(Metric.known(1.5), Metric.unknown(), AvailabilityState.unknown()));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityAvailability(Metric.known(-0.1), Metric.unknown(), AvailabilityState.unknown()));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityAvailability(Metric.known(Double.NaN), Metric.unknown(), AvailabilityState.unknown()));
    }

    @Test
    void malformedComponentsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new AbilityAvailability(null, Metric.unknown(), AvailabilityState.unknown()));
        assertThrows(NullPointerException.class,
                () -> new AbilityAvailability(Metric.unknown(), null, AvailabilityState.unknown()));
        assertThrows(NullPointerException.class,
                () -> new AbilityAvailability(Metric.unknown(), Metric.unknown(), null));
        assertThrows(IllegalArgumentException.class, () -> new Metric.Known<>(null));
        assertThrows(IllegalArgumentException.class, () -> new AvailabilityState.Unavailable(" "));
        assertThrows(NullPointerException.class, () -> new AvailabilityState.Unavailable(null));
    }

    @Test
    void duplicateProviderDeclarationFailsLoudly() {
        AvailabilityProviders.declare(FIREBALL, (player, id) -> AbilityAvailability.allUnknown());
        assertThrows(IllegalStateException.class,
                () -> AvailabilityProviders.declare(FIREBALL, (player, id) -> AbilityAvailability.allUnknown()));
        assertTrue(AvailabilityProviders.has(FIREBALL));
        assertTrue(AvailabilityProviders.remove(FIREBALL));
        assertFalse(AvailabilityProviders.has(FIREBALL));
    }

    @Test
    void nullArgumentsToRegistryAreHandledWithoutThrowing() {
        assertFalse(AvailabilityProviders.availabilityFor(null, null).isPresent());
        assertFalse(AvailabilityProviders.has(null));
        assertFalse(AvailabilityProviders.remove(null));
        assertThrows(IllegalArgumentException.class, () -> AvailabilityProviders.declare(null, (p, id) -> null));
        assertThrows(IllegalArgumentException.class, () -> AvailabilityProviders.declare(FIREBALL, null));
    }

    @Test
    void metricFactoriesAndAccessors() {
        Metric<Double> known = Metric.known(0.25);
        Metric<Double> unknown = Metric.unknown();
        assertTrue(known.isKnown());
        assertFalse(unknown.isKnown());
        assertEquals(Optional.of(0.25), known.knownValue());
        assertEquals(Optional.empty(), unknown.knownValue());

        Player ignored = null; // provider boundary is side-disciplined; tests pass no player
        assertEquals(Optional.empty(),
                AvailabilityProviders.availabilityFor(ignored, FIREBALL));
    }
}
