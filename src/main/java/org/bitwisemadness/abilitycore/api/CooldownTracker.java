package org.bitwisemadness.abilitycore.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Core-side cooldown storage for {@link ChargingModel}-analogous
 * {@code Owner.EXTERNAL} cooldowns (REQ-CORE-05).
 *
 * <p>Only abilities whose {@code CooldownOwners} declaration says EXTERNAL are
 * tracked here; NATIVE-cooldown abilities are checked and started by their
 * provider's own pipeline (ISS {@code PlayerCooldowns}/{@code MagicManager},
 * Tensura's {@code SKILL_UPDATE_COOLDOWN}) - core never duplicates that state
 * and never re-syncs it (client availability projects the provider's own
 * synced state per REQ-CORE-08/REQ-HOT-08).</p>
 *
 * <p>The built-in implementation is deliberately <b>not persisted</b>
 * (documented decision from core#15-adjacent review of core#16's notes:
 * core-owned cooldowns recompute from runtime state; a server restart clears
 * them - acceptable for EXTERNAL abilities until a persistence requirement is
 * filed as its own issue).</p>
 */
public interface CooldownTracker {

    /** Remaining cooldown in ticks (0 = ready). Read-only. */
    long remainingTicks(ResourceLocation abilityId, ServerPlayer player);

    /**
     * Starts (or restarts) the cooldown. Called only post-activation by the
     * pipeline - never at initiation - mirroring native start timing
     * (ISS starts cooldowns inside {@code castSpell}, not on cast start).
     */
    void start(ResourceLocation abilityId, ServerPlayer player, long durationTicks);
}
