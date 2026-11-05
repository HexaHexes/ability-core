package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Declared per-player icon providers, one per ability id (core#62) - the
 * exact mirror of {@link AvailabilityProviders}: same {@code declare} /
 * query / {@code has} / {@code remove} shape, same absence semantics, so a
 * consumer never branches on {@code ModList.isLoaded} for <em>icon data</em>.
 *
 * <p>The query {@link #iconFor} is the first step of the documented
 * resolution order (provider -&gt; the definition's static {@code icon} -&gt;
 * the consumer's placeholder; the chain lives in the consumer and is
 * specified in {@code docs/api-surface.md}). It returns
 * {@link Optional#empty()} when no provider is declared, and equally when a
 * provider answers null or <b>throws</b>: a failure is caught, logged
 * <b>once per ability</b> (render time calls this every frame) and the
 * resolution falls down the order - a broken provider can never crash or
 * blank a slot.</p>
 *
 * <p>Render-time, client-state only: providers read client-known state;
 * declaring one never triggers or requires a server sync (icons are
 * display-only - no payload, no server trust).</p>
 *
 * <p>Exactly one provider per ability id: duplicate declaration fails loudly
 * (two providers claiming one ability is the ambiguity that produces a
 * flickering icon). Nothing in core registers a provider - the surface is
 * optional, and a consumer that declares nothing behaves exactly like
 * today's static {@code iconId} path (grep-gated by the fixtures).</p>
 */
public final class IconProviders {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, IconProvider> PROVIDERS = new ConcurrentHashMap<>();

    /** Ids whose failure has already been logged - the once-per-ability rule. */
    private static final Set<ResourceLocation> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private IconProviders() {
    }

    /**
     * Declares the icon provider for one ability id.
     *
     * @throws IllegalArgumentException if the id or the provider is null
     * @throws IllegalStateException    if the ability already has a provider
     *         (loud ownership conflict, never silent overwrite)
     */
    public static void declare(ResourceLocation abilityId, IconProvider provider) {
        if (abilityId == null) {
            throw new IllegalArgumentException("abilityId must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null for " + abilityId);
        }
        IconProvider previous = PROVIDERS.putIfAbsent(abilityId, provider);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate icon provider for " + abilityId + " - two providers claim one ability");
        }
    }

    /**
     * Projects one ability's icon for one player, or empty when no provider
     * is declared (or the provider failed) - the caller falls to the
     * definition's static icon next. Never throws.
     *
     * @param player    the player to project (nullable for tests)
     * @param abilityId the ability to project
     * @return the provider's icon, or empty to fall down the resolution order
     */
    public static Optional<ResourceLocation> iconFor(@Nullable Player player, ResourceLocation abilityId) {
        IconProvider provider = abilityId == null ? null : PROVIDERS.get(abilityId);
        if (provider == null) {
            return Optional.empty();
        }
        ResourceLocation icon;
        try {
            icon = provider.iconFor(player, abilityId);
        } catch (Exception e) {
            logFailureOnce(abilityId, "threw", e);
            return Optional.empty();
        }
        if (icon == null) {
            logFailureOnce(abilityId, "returned null", null);
            return Optional.empty();
        }
        return Optional.of(icon);
    }

    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && PROVIDERS.containsKey(abilityId);
    }

    /** Test isolation / re-declaration: also re-arms the once-per-ability log. */
    public static boolean remove(ResourceLocation abilityId) {
        if (abilityId != null) {
            LOGGED_FAILURES.remove(abilityId);
            return PROVIDERS.remove(abilityId) != null;
        }
        return false;
    }

    /** First failure for this id logs; later frames fall back silently. */
    private static void logFailureOnce(ResourceLocation abilityId, String how, @Nullable Exception e) {
        if (LOGGED_FAILURES.add(abilityId)) {
            LOGGER.warn("Icon provider for {} {} - falling back to the definition icon", abilityId, how, e);
        }
    }
}
