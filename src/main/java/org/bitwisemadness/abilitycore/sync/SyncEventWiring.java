package org.bitwisemadness.abilitycore.sync;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Server-side event wiring that fires {@link AbilitySyncNeededEvent} on
 * player lifecycle transitions (REQ-CORE-08).
 *
 * <p>Registered on the NeoForge game bus. Consumers listen for
 * {@code AbilitySyncNeededEvent} on the same bus and send their state
 * payloads. Core does not send game data itself.</p>
 *
 * <p>Dedicated-server safety: this class contains no client-only references;
 * the event is fired server-side and consumed by whatever is listening.</p>
 */
@EventBusSubscriber(modid = "ability_core")
public final class SyncEventWiring {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SyncEventWiring() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("Firing AbilitySyncNeededEvent (LOGIN) for {}", player.getName().getString());
            AbilitySyncNeededEvent.fire(player, AbilitySyncNeededEvent.Reason.LOGIN);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("Firing AbilitySyncNeededEvent (RESPAWN) for {}", player.getName().getString());
            AbilitySyncNeededEvent.fire(player, AbilitySyncNeededEvent.Reason.RESPAWN);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("Firing AbilitySyncNeededEvent (DIMENSION_CHANGE) for {}", player.getName().getString());
            AbilitySyncNeededEvent.fire(player, AbilitySyncNeededEvent.Reason.DIMENSION_CHANGE);
        }
    }
}
