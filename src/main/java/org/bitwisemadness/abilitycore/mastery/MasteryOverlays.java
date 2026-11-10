package org.bitwisemadness.abilitycore.mastery;

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
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Datapack mastery-spec overlays on the built-in ability registry
 * (REQ-CORE-12) - the data-map twin of {@code DefinitionOverlays}: JSON at
 * {@code data/<ns>/data_maps/ability_core/mastery_spec/<name>.json}, live
 * reads (NeoForge refills the store on datapack {@code /reload}), no
 * listener, no cache.
 *
 * <p>Not synced to clients: awards, effect reads and the query answer all
 * run server-side, and display rides the explicit mastery query payload
 * (REQ-CORE-12's contract) - so the overlay never needs to exist on a
 * client.</p>
 *
 * <p><b>Opt-in stays single-sourced</b>: an overlay only ever tunes a
 * spec that code declared ({@link MasteryDeclarations}); an id without a
 * declaration has no system at all, overlay or not. The test seam
 * {@link #installOverlaysForTests} mirrors
 * {@link AbilityRegistries#installRegistryForTests}.</p>
 */
@EventBusSubscriber(modid = AbilityCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MasteryOverlays {

    /** {@code data/<ns>/data_maps/ability_core/mastery_spec/<name>.json} */
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "mastery_spec");

    /** Per-id spec overlays, server-side only (never synced). */
    public static final DataMapType<AbilityDefinition, MasterySpecOverlay> TYPE =
            DataMapType.builder(TYPE_ID, AbilityRegistries.ABILITY_REGISTRY_KEY,
                    MasterySpecOverlay.CODEC).build();

    @Nullable
    private static volatile Map<ResourceLocation, MasterySpecOverlay> testOverlays;

    private MasteryOverlays() {
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
    public static MasterySpecOverlay get(ResourceLocation abilityId) {
        Map<ResourceLocation, MasterySpecOverlay> installed = testOverlays;
        if (installed != null) {
            return installed.get(abilityId);
        }
        Registry<AbilityDefinition> registry = AbilityRegistries.registry();
        if (registry == null) {
            return null;
        }
        return registry.getData(TYPE,
                ResourceKey.create(AbilityRegistries.ABILITY_REGISTRY_KEY, abilityId));
    }

    /**
     * The spec every award/effect read uses: the code declaration with
     * any datapack overlay merged at lookup, or null when the id declared
     * nothing (the opt-out answer - an overlay alone never opts an id in).
     * Live: a {@code /reload} applies on the next read.
     *
     * @param abilityId the declared ability
     * @return the merged spec, or null when undeclared
     */
    @Nullable
    public static MasterySpec specFor(ResourceLocation abilityId) {
        MasterySpec base = MasteryDeclarations.get(abilityId).orElse(null);
        if (base == null) {
            return null;
        }
        MasterySpecOverlay overlay = get(abilityId);
        return overlay != null ? overlay.mergeInto(base) : base;
    }

    /**
     * Test-support: swaps the overlay source wholesale (mirrors
     * {@link AbilityRegistries#installRegistryForTests}); always reset in
     * teardown.
     */
    @ApiStatus.Internal
    public static void installOverlaysForTests(
            @Nullable Map<ResourceLocation, MasterySpecOverlay> overlays) {
        testOverlays = overlays == null ? null : Map.copyOf(overlays);
    }
}
