package org.bitwisemadness.abilitycore.api;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The mode-read interface (issue #113, findings #112 recommendation 2): a
 * server-authoritative read over the ownership-declared contract, the
 * {@link CooldownContract} mirror. Exactly one answer source: an ability
 * declared NATIVE with a projection answers through
 * {@link ModeOwners.NativeProjection} - the provider's live mode <em>exactly,
 * never a derived value - and every other case (undeclared, declared without
 * a projection) answers empty, because core must never claim a mode it
 * cannot see (REQ-HOT-08 unknown-vs-known discipline).
 *
 * <p>This reads what an ability <em>is in</em> right now. The value a player
 * has <em>set</em> on a hotbar slot is hotbar#29's per-slot state - a
 * different question with a different owner, and this facade never guesses
 * one from the other.</p>
 */
public final class ModeContract {

    private ModeContract() {
    }

    /**
     * The mode this player's ability is actually in, or empty when core
     * cannot tell.
     *
     * @param player    the player whose live mode to read (may be null only
     *                  in tests with null-safe projections)
     * @param abilityId the ability to read
     * @return the provider-reported mode id; empty = unknown, never a guess
     */
    public static Optional<String> currentMode(@Nullable ServerPlayer player,
            ResourceLocation abilityId) {
        if (abilityId == null) {
            return Optional.empty();
        }
        ModeOwners.NativeProjection projection = ModeOwners.projectionFor(abilityId);
        if (projection == null) {
            return Optional.empty();
        }
        Optional<String> answer = projection.modeId(player, abilityId);
        // a projection that answers null reports "cannot tell" - never a mode
        return answer == null ? Optional.empty() : answer;
    }
}
