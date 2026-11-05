package org.bitwisemadness.abilitycore.registry;

import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Datapack definition overlays on the built-in ability registry (issue #12's
 * data-map sub-part; the recorded verdict - data maps attached to built-in
 * entries, not a second JSON registry, so id sync semantics and the
 * client-requirement surface stay unchanged).
 *
 * <p>The overlay rides NeoForge's registry data maps: JSON at
 * {@code data/<ns>/data_maps/ability_core/abilities/definition_overlay.json},
 * registered with a <b>network codec</b> (non-mandatory) so clients sync it
 * and the menu renders the same merged definitions the server charges
 * against. Lookups are <b>live</b>: NeoForge refills the registry's data-map
 * store on datapack reload, so {@link #get} answers with fresh values on the
 * next lookup - no listener, no cache, no re-derivation.</p>
 *
 * <p>Registration happens on {@link RegisterDataMapTypesEvent} (mod bus).
 * The test seam {@link #installOverlaysForTests} mirrors
 * {@link AbilityRegistries#installRegistryForTests}.</p>
 */
@EventBusSubscriber(modid = AbilityCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DefinitionOverlays {

    /** {@code data/<ns>/data_maps/ability_core/abilities/<this>.json} */
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "definition_overlay");

    /** Per-id definition overlays, synced to clients (non-mandatory). */
    public static final DataMapType<AbilityDefinition, DefinitionOverlay> TYPE =
            DataMapType.builder(TYPE_ID, AbilityRegistries.ABILITY_REGISTRY_KEY, DefinitionOverlay.CODEC)
                    .synced(DefinitionOverlay.CODEC, false)
                    .build();

    @Nullable
    private static volatile Map<ResourceLocation, DefinitionOverlay> testOverlays;

    private DefinitionOverlays() {
    }

    @SubscribeEvent
    public static void onRegisterDataMapTypes(RegisterDataMapTypesEvent event) {
        event.register(TYPE);
    }

    /**
     * The overlay attached to this id, or null. Live read (never cached):
     * a datapack {@code /reload} changes the answer on the next lookup.
     */
    @Nullable
    public static DefinitionOverlay get(ResourceLocation abilityId) {
        Map<ResourceLocation, DefinitionOverlay> installed = testOverlays;
        if (installed != null) {
            return installed.get(abilityId);
        }
        Registry<AbilityDefinition> registry = AbilityRegistries.registry();
        if (registry == null) {
            return null;
        }
        return registry.getData(TYPE, ResourceKey.create(AbilityRegistries.ABILITY_REGISTRY_KEY, abilityId));
    }

    /**
     * Test-support: swaps the overlay source wholesale (mirrors
     * {@link AbilityRegistries#installRegistryForTests}); always reset in
     * teardown.
     */
    public static void installOverlaysForTests(@Nullable Map<ResourceLocation, DefinitionOverlay> overlays) {
        testOverlays = overlays == null ? null : Map.copyOf(overlays);
    }
}
