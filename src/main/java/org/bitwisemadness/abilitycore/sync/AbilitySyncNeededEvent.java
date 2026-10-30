package org.bitwisemadness.abilitycore.sync;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Fired server-side when a player needs a full ability-state snapshot
 * (login, respawn, dimension change, provider-set change).
 *
 * <p>Consumers (hotbar, bridge, menu) listen for this event and send their
 * state payloads to the player. Core does NOT send game data itself - it
 * fires the signal that tells consumers "now is the time to sync".</p>
 *
 * <p>This is the ISS-proven pattern: full snapshot on lifecycle boundaries,
 * deltas on mutation (verified against ISS {@code ServerPlayerEvents} and the
 * NeoForge 1.21.1 networking research in core#4).</p>
 *
 * <p>Fired on the NeoForge game bus (server side only).</p>
 *
 * @see PlayerEvent.PlayerLoggedInEvent
 * @see PlayerEvent.PlayerRespawnEvent
 * @see PlayerEvent.PlayerChangedDimensionEvent
 */
public class AbilitySyncNeededEvent extends Event {

    private final net.minecraft.server.level.ServerPlayer player;
    private final Reason reason;

    public enum Reason {
        /** Player joined the server. */
        LOGIN,
        /** Player respawned (death or end-portal return). */
        RESPAWN,
        /** Player changed dimension (client caches are unreliable across dimensions). */
        DIMENSION_CHANGE
    }

    public AbilitySyncNeededEvent(net.minecraft.server.level.ServerPlayer player, Reason reason) {
        this.player = player;
        this.reason = reason;
    }

    public net.minecraft.server.level.ServerPlayer player() {
        return player;
    }

    public Reason reason() {
        return reason;
    }

    /** Convenience: fires the appropriate event for a player lifecycle transition. */
    public static void fire(net.minecraft.server.level.ServerPlayer player, Reason reason) {
        if (!player.level().isClientSide()) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new AbilitySyncNeededEvent(player, reason));
        }
    }

    /** True when running on the logical server (defensive; the event is server-only by construction). */
    public boolean isServerSide() {
        return !player.level().isClientSide();
    }

    /** The logical side this event fires on (always SERVER for this event). */
    public LogicalSide side() {
        return LogicalSide.SERVER;
    }
}
