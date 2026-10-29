package org.bitwisemadness.tensuraabilitycore.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.bitwisemadness.tensuraabilitycore.TensuraAbilityCore;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;

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
 */
public final class AbilityRegistries {

    /** Registry key providers register against on their own mod bus. */
    public static final ResourceKey<Registry<AbilityDefinition>> ABILITY_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(TensuraAbilityCore.MOD_ID, "abilities"));

    /**
     * The live registry. Null until mod loading constructs it; never null once
     * the game reaches any gameplay state (GameTest-asserted).
     */
    public static Registry<AbilityDefinition> ABILITY_REGISTRY;

    private AbilityRegistries() {
    }

    /** Mod-bus listener: creates and registers the registry (both sides). */
    public static void onNewRegistry(NewRegistryEvent event) {
        ABILITY_REGISTRY = event.create(new RegistryBuilder<>(ABILITY_REGISTRY_KEY)
                .sync(true)
                .onAdd(AbilityRegistries::validateEntry));
    }

    private static void validateEntry(Registry<AbilityDefinition> registry, int id,
            ResourceKey<AbilityDefinition> key, AbilityDefinition value) {
        value.validateOrThrow(key.location());
    }
}
