package org.bitwisemadness.abilitycore.api;

import java.time.Duration;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The cooldown check interface (REQ-CORE-05, core#16): server-authoritative
 * reads over the ownership-declared contract. Exactly one answer source per
 * ability: EXTERNAL reads the declared {@link CooldownTracker} (the same store
 * the pipeline gate reads), NATIVE with a projection reads the provider's own
 * state through {@link CooldownOwners.NativeProjection} - core reports the
 * provider's remaining <em>exactly</em>, never a derived value - and NATIVE
 * without a projection answers empty (unknown: core must neither claim ready
 * nor claim cooling; the provider's pipeline is the gate).
 *
 * <p>Client side reads projections only, never this facade: NATIVE cooldown
 * state is synced by its provider (ISS syncs its own - hotbar#2 section 3) and
 * mapped into the overlay tuple by the provider's {@link AvailabilityProvider};
 * EXTERNAL state reaches clients through a consumer's snapshot payload
 * (ADR-003). No core packet carries either - the compiled payload inventory
 * is pinned by {@code SyncContractTest}.</p>
 *
 * <p>Start is deliberately not part of this facade: the only start site is
 * {@code ExternalCooldownStep.startExternalCooldown}, called post-activation by
 * the pipeline, so a cooldown always starts at the provider's native moment
 * (core#16 AC2/AC4). A public start here would be a second start site.</p>
 */
public final class CooldownContract {

    private static final long MILLIS_PER_TICK = 50L;

    private CooldownContract() {
    }

    /**
     * The remaining cooldown for this ability, or empty when core cannot tell
     * (undeclared id, or NATIVE without a registered projection). One tick is
     * 50 ms; a source reporting a negative value clamps to zero so the gate
     * and this query - which read the same source - can never disagree.
     *
     * @param abilityId the ability to read
     * @param player    the player whose cooldown to read (may be null only in
     *                  tests with null-safe sources; production passes a live player)
     * @return the remaining duration; empty only when core genuinely cannot answer
     */
    public static Optional<Duration> remaining(ResourceLocation abilityId, @Nullable ServerPlayer player) {
        if (abilityId == null) {
            return Optional.empty();
        }
        Optional<CooldownTracker> tracker = CooldownOwners.trackerFor(abilityId);
        if (tracker.isPresent()) {
            return Optional.of(toDuration(tracker.get().remainingTicks(abilityId, player)));
        }
        CooldownOwners.NativeProjection projection = CooldownOwners.projectionFor(abilityId);
        if (projection == null) {
            return Optional.empty();
        }
        return Optional.of(toDuration(projection.remainingTicks(abilityId, player)));
    }

    /**
     * True only while an affirmative answer says the ability is cooling.
     * Unknown (empty) is never a cooldown claim - core gates only EXTERNAL,
     * whose tracker always answers, and NATIVE gating belongs to the provider.
     */
    public static boolean isOnCooldown(ResourceLocation abilityId, @Nullable ServerPlayer player) {
        return remaining(abilityId, player).filter(remaining -> !remaining.isZero()).isPresent();
    }

    private static Duration toDuration(long remainingTicks) {
        return Duration.ofMillis(Math.max(0L, remainingTicks) * MILLIS_PER_TICK);
    }
}
