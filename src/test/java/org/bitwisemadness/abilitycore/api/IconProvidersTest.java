package org.bitwisemadness.abilitycore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Level;
import org.bitwisemadness.abilitycore.LogCapture;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Per-player icon projection (core#62): the AvailabilityProviders-shaped
 * surface, the three-step consumer resolution order (provider -> definition
 * icon -> placeholder), the broken-provider failure contract (caught, logged
 * once per ability, falls down the order), and the grep gate proving nothing
 * in core mandates a registration.
 */
class IconProvidersTest {

    private static final ResourceLocation PRESS =
            ResourceLocation.fromNamespaceAndPath("tensura", "press");
    private static final ResourceLocation STILL =
            ResourceLocation.fromNamespaceAndPath("tensura", "still");
    private static final ResourceLocation PLAIN =
            ResourceLocation.fromNamespaceAndPath("tensura", "plain");
    private static final ResourceLocation THROWER =
            ResourceLocation.fromNamespaceAndPath("tensura", "thrower");
    private static final ResourceLocation UNREGISTERED =
            ResourceLocation.fromNamespaceAndPath("tensura", "unregistered");

    private static final ResourceLocation PRESS_STATIC =
            ResourceLocation.fromNamespaceAndPath("tensura", "press_static");
    private static final ResourceLocation PRESS_TIER3 =
            ResourceLocation.fromNamespaceAndPath("tensura", "press_tier3");
    private static final ResourceLocation STILL_STATIC =
            ResourceLocation.fromNamespaceAndPath("tensura", "still");
    private static final ResourceLocation THROWER_FALLBACK =
            ResourceLocation.fromNamespaceAndPath("tensura", "thrower_fallback");
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("tensura", "placeholder");

    private Registry<AbilityDefinition> previousRegistry;

    @BeforeEach
    void installFixtureRegistry() {
        previousRegistry = AbilityRegistries.registry();
        var fixture = new MappedRegistry<AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, Lifecycle.stable(), false);
        Registry.register(fixture, PRESS, definition(PRESS_STATIC));
        Registry.register(fixture, STILL, definition(STILL_STATIC));
        Registry.register(fixture, PLAIN, definition(null));
        Registry.register(fixture, THROWER, definition(THROWER_FALLBACK));
        AbilityRegistries.installRegistryForTests(fixture);
    }

    @AfterEach
    void tearDown() {
        AbilityRegistries.installRegistryForTests(previousRegistry);
        IconProviders.remove(PRESS);
        IconProviders.remove(STILL);
        IconProviders.remove(PLAIN);
        IconProviders.remove(THROWER);
        IconProviders.remove(UNREGISTERED);
    }

    private static AbilityDefinition definition(ResourceLocation iconId) {
        return new AbilityDefinition("tensura.skill.icon", iconId,
                ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                ActivationType.INSTANT, List.of(), ConsumptionStrategies.ALL, Set.of(), Map.of());
    }

    /**
     * The consumer contract exactly as docs/api-surface.md documents it:
     * provider -> the definition's static icon -> this caller's placeholder.
     */
    private static ResourceLocation resolveAsConsumer(
            net.minecraft.world.entity.player.Player player, ResourceLocation id, ResourceLocation placeholder) {
        ResourceLocation icon = IconProviders.iconFor(player, id).orElse(null);
        if (icon == null) {
            icon = AbilityDiscovery.get(id).map(AbilityDefinition::iconId).orElse(null);
        }
        if (icon == null) {
            icon = placeholder;
        }
        return icon;
    }

    @Test
    void declaredProviderAnswerWinsForThatPlayer() {
        // AC1: fixture provider for X returns A -> consumer resolution returns A
        IconProviders.declare(PRESS, (player, id) -> PRESS_TIER3);

        assertTrue(IconProviders.has(PRESS));
        assertEquals(Optional.of(PRESS_TIER3), IconProviders.iconFor(null, PRESS));
        assertEquals(PRESS_TIER3, resolveAsConsumer(null, PRESS, PLACEHOLDER),
                "AC1: the provider's per-player answer is what the consumer resolves");
        assertEquals(PRESS_STATIC,
                AbilityDiscovery.get(PRESS).orElseThrow().iconId(),
                "the definition's static icon is unchanged - projection never mutates it");

        assertTrue(IconProviders.remove(PRESS));
        assertFalse(IconProviders.has(PRESS));
        assertEquals(PRESS_STATIC, resolveAsConsumer(null, PRESS, PLACEHOLDER),
                "after removal the chain lands on the static icon (no provider, no crash)");
    }

    @Test
    void resolutionWithoutAnyProviderFallsToStaticIconThenPlaceholder() {
        // AC2: no provider for X -> definition.iconId(); null -> placeholder
        assertTrue(IconProviders.iconFor(null, STILL).isEmpty());
        assertEquals(STILL_STATIC, resolveAsConsumer(null, STILL, PLACEHOLDER),
                "step 2: the definition's static icon");
        assertEquals(PLACEHOLDER, resolveAsConsumer(null, PLAIN, PLACEHOLDER),
                "step 3: static icon null -> the consumer's placeholder");
        assertEquals(PLACEHOLDER, resolveAsConsumer(null, UNREGISTERED, PLACEHOLDER),
                "unknown id -> placeholder, never a crash and never null");
    }

    @Test
    void brokenProviderIsLoggedOnceAndFallsDownTheOrder() {
        // AC3: throws -> caught, logged once per ability, fallback used; a
        // null answer is the same fall with its own once-per-ability log
        IconProviders.declare(THROWER, (player, id) -> {
            throw new IllegalStateException("render state unavailable");
        });
        IconProviders.declare(PRESS, (player, id) -> null);

        try (LogCapture logs = LogCapture.forLogger(IconProviders.class, Level.WARN)) {
            for (int frame = 0; frame < 5; frame++) {
                assertTrue(IconProviders.iconFor(null, THROWER).isEmpty(),
                        "a throwing provider must never propagate - frame " + frame);
            }
            assertTrue(IconProviders.iconFor(null, PRESS).isEmpty(), "null answer falls down the order");
            assertTrue(IconProviders.iconFor(null, PRESS).isEmpty());
            assertEquals(2, logs.messages().size(),
                    "one log line per failing ability, not one per frame");
        }

        assertEquals(THROWER_FALLBACK, resolveAsConsumer(null, THROWER, PLACEHOLDER),
                "AC3: the definition icon is used - no crash, no blank slot");
        assertEquals(PRESS_STATIC, resolveAsConsumer(null, PRESS, PLACEHOLDER),
                "a null-answered provider falls to the static icon too");
    }

    @Test
    void declaringNothingIsByteIdenticalToTheStaticIconPath() {
        // AC4 fixture: with zero declarations the consumer chain answers exactly
        // what today's static iconId path answers, for every definition shape
        assertTrue(IconProviders.iconFor(null, PRESS).isEmpty());
        assertTrue(IconProviders.iconFor(null, STILL).isEmpty());
        assertEquals(PRESS_STATIC, resolveAsConsumer(null, PRESS, PLACEHOLDER));
        assertEquals(STILL_STATIC, resolveAsConsumer(null, STILL, PLACEHOLDER));
        assertEquals(PLACEHOLDER, resolveAsConsumer(null, PLAIN, PLACEHOLDER));
    }

    @Test
    void onlyTheApiFilesThemselvesMayReferenceIconProviders() throws Exception {
        // AC4 grep gate: no bootstrap, pipeline or registry code may wire the
        // surface - optional means declaring nothing must stay the default
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root),
                "expected a source directory (test working dir is the project dir): " + root);
        List<String> referencing = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains("IconProvider")) {
                    referencing.add(file.toString().replace('\\', '/'));
                }
            }
        }
        assertEquals(List.of(
                "src/main/java/org/bitwisemadness/abilitycore/api/IconProvider.java",
                "src/main/java/org/bitwisemadness/abilitycore/api/IconProviders.java"),
                referencing.stream().sorted().toList(),
                "only the api files themselves may name IconProvider(s) - no mandatory registration");
    }

    @Test
    void surfaceShapeMirrorsAvailabilityProviders() {
        // the "mirrors AvailabilityProviders exactly" requirement, pinned:
        // same validation rules and the same loud duplicate conflict
        assertThrows(IllegalArgumentException.class, () -> IconProviders.declare(null, (p, i) -> null));
        assertThrows(IllegalArgumentException.class, () -> IconProviders.declare(PRESS, null));
        IconProviders.declare(PRESS, (player, id) -> PRESS_TIER3);
        assertThrows(IllegalStateException.class,
                () -> IconProviders.declare(PRESS, (player, id) -> PRESS_TIER3),
                "two providers claim one ability - loud conflict, never overwrite");
        assertFalse(IconProviders.has(null));
        assertFalse(IconProviders.remove(null));
        assertTrue(IconProviders.iconFor(null, null).isEmpty(),
                "null id is a miss, never a crash");
    }
}
