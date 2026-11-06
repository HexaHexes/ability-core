package org.bitwisemadness.abilitycore.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Ability-mode ownership (issue #113, findings #112 recommendation 2): the
 * exact {@link CooldownOwners} mirror for live mode state - exactly one
 * declaration per ability, {@link #declareNative(ResourceLocation)} records
 * that the provider owns the state, and the optional
 * {@link NativeProjection} lets core answer what mode a player's ability is
 * <em>actually in</em> without ever storing it.
 *
 * <p>Core stores the function, never the state: the provider (ARR and
 * friends) stays authoritative, there is no core-side copy to drift or
 * re-sync, and a mode transition is visible on the next read. This is
 * deliberately distinct from a hotbar slot's stored mode <em>setting</em>
 * (hotbar#29) - two questions, two owners.</p>
 */
public final class ModeOwners {

    /**
     * Read-through view of a provider's OWN live mode state. Core stores
     * this function, never the state; returning empty means "cannot tell" -
     * never a guessed mode (REQ-HOT-08 discipline). Server-side: clients
     * read their provider's own synced state, never this facade.
     */
    @FunctionalInterface
    public interface NativeProjection {
        Optional<String> modeId(ServerPlayer player, ResourceLocation abilityId);
    }

    /** Empty = declared without a projection; missing key = undeclared. */
    private static final Map<ResourceLocation, Optional<NativeProjection>> ENTRIES =
            new ConcurrentHashMap<>();

    private ModeOwners() {
    }

    /** Default semantics: the provider owns its mode state and offers no core read. */
    public static void declareNative(ResourceLocation abilityId) {
        declare(abilityId, Optional.empty());
    }

    /**
     * NATIVE with a read-through projection: {@link ModeContract#currentMode}
     * then answers the provider's mode exactly (or empty when the projection
     * cannot tell).
     *
     * @throws IllegalArgumentException when the projection is null - use
     *         {@link #declareNative(ResourceLocation)} without one
     */
    public static void declareNative(ResourceLocation abilityId, NativeProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException(
                    "projection must not be null - use declareNative(id) without one");
        }
        declare(abilityId, Optional.of(projection));
    }

    private static void declare(ResourceLocation abilityId, Optional<NativeProjection> projection) {
        if (abilityId == null) {
            throw new IllegalArgumentException("ability id must not be null");
        }
        Optional<NativeProjection> previous = ENTRIES.putIfAbsent(abilityId, projection);
        if (previous != null) {
            throw new IllegalStateException("Duplicate mode-owner declaration for " + abilityId
                    + " - exactly one owner per ability (issue #113)");
        }
    }

    /**
     * The registered projection, or null (declared without one, or
     * undeclared) - {@link ModeContract} maps null to unknown.
     */
    @Nullable
    public static NativeProjection projectionFor(ResourceLocation abilityId) {
        Optional<NativeProjection> entry = abilityId == null ? null : ENTRIES.get(abilityId);
        return entry == null ? null : entry.orElse(null);
    }

    /** Test isolation / future reload paths. */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && ENTRIES.remove(abilityId) != null;
    }
}
