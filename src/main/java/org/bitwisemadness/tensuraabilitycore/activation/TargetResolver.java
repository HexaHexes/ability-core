package org.bitwisemadness.tensuraabilitycore.activation;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side resolution of target hints (REQ-CORE-06).
 *
 * <p>Isolated behind this interface so the validation step is unit-testable
 * without a live {@code ServerLevel}, and so future resolution policies
 * (dimension guards, tracking-range caps) have exactly one place to live.
 * The production default resolves against the <em>requesting player's own
 * level</em> - a hint pointing outside it cannot resolve, hence is refused.</p>
 *
 * <p>The resolved value is deliberately <b>opaque</b> at this boundary
 * ({@code Object}): core never dereferences it - it only refuses when the
 * resolver returns null and stashes the value for the provider's handler to
 * consume. Providers own the cast to their concrete type (production default
 * resolves real {@code Entity} instances via the requester's
 * {@code ServerLevel} - the UUID lookup lives there on 1.21.1).</p>
 */
public interface TargetResolver {

    /** The requesting player's own level (production default). */
    TargetResolver DEFAULT = new TargetResolver() {
        @Override
        @Nullable
        public Object resolveEntity(UUID entityId, ServerPlayer player) {
            // ServerLevel owns the UUID lookup on 1.21.1 (Level only has the int form)
            return ((net.minecraft.server.level.ServerLevel) player.level()).getEntity(entityId);
        }

        @Override
        public boolean isLoaded(net.minecraft.core.BlockPos pos, ServerPlayer player) {
            return player.level().isLoaded(pos);
        }
    };

    /**
     * @return the opaque server-resolved value for the hint, or null when the
     *         hint cannot resolve (-> REFUSED_INVALID_TARGET, never a guess)
     */
    @Nullable
    Object resolveEntity(UUID entityId, ServerPlayer player);

    /** Whether the position sits in a loaded chunk (-> refuse if not). */
    boolean isLoaded(net.minecraft.core.BlockPos pos, ServerPlayer player);
}
