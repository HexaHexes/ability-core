package org.bitwisemadness.abilitycore.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The core-owned ability registry (REQ-CORE-01, ADR-001).
 *
 * <p>A built-in NeoForge registry: entries are registered <em>in code</em> by
 * providers on their own mod buses through the standard
 * {@code RegisterEvent} for {@link #ABILITY_REGISTRY_KEY}. Datapack extension
 * of entry data is served separately via data maps (ADR-001), not by making
 * this registry datapack-backed.</p>
 *
 * <p>Registration semantics: duplicate ids are rejected loudly by vanilla
 * {@code Registry.register}; malformed definitions are rejected by the
 * {@code onAdd} validation callback, which also runs when the registry is
 * rebuilt from a server sync - both sides validate identically.</p>
 *
 * <p>Sync semantics (verified against NeoForge 1.21.1 source): {@code sync(true)}
 * synchronises <em>numerical ids and the entry set</em> to clients via
 * {@code RegistrySnapshot} (id-to-key maps only - entry <em>content</em> is
 * never sent by the platform). Client-known definition content therefore rides
 * explicit payloads (REQ-CORE-08 catalog payload; recorded on core#19).</p>
 *
 * <p>The live registry is exposed through {@link #registry()} rather than a
 * public field: null-until-loaded semantics stay identical, and the field is
 * not externally mutable (keeps the SpotBugs baseline at zero findings).</p>
 */
public final class AbilityRegistries {

    /** Registry key providers register against on their own mod bus. */
    public static final ResourceKey<Registry<AbilityDefinition>> ABILITY_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "abilities"));

    private static Registry<AbilityDefinition> registry;

    private AbilityRegistries() {
    }

    /**
     * The live registry, or null until mod loading constructs it (never null
     * once the game reaches gameplay state - GameTest-asserted).
     */
    @Nullable
    public static Registry<AbilityDefinition> registry() {
        return registry;
    }

    /** False until {@link #onNewRegistry} ran (login-window placeholder rule). */
    public static boolean isReady() {
        return registry != null;
    }

    /** Mod-bus listener: creates and registers the registry (both sides). */
    public static void onNewRegistry(NewRegistryEvent event) {
        registry = event.create(new RegistryBuilder<>(ABILITY_REGISTRY_KEY)
                .sync(true)
                .onAdd(AbilityRegistries::validateEntry));
    }

    /**
     * Test-support: installs an isolated fixture registry (unit tests cannot
     * boot FML). Production code must never call this - use the real registry
     * from {@link #registry()}.
     */
    @ApiStatus.Internal
    public static void installRegistryForTests(@Nullable Registry<AbilityDefinition> fixture) {
        registry = fixture;
    }

    private static void validateEntry(Registry<AbilityDefinition> registry, int id,
            ResourceKey<AbilityDefinition> key, AbilityDefinition value) {
        value.validateOrThrow(key.location());
    }
}
