package org.bitwisemadness.abilitycore.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Menu visibility (core#124): whether the ability menu lists one ability
 * for one player. Exactly one handler per declared id, declared at mod
 * construction on both sides (ADR-003 same-construction: the client menu
 * reads it, so no payload exists) - duplicates fail loudly, the
 * dual-handler state unconstructible like every other contract here.
 *
 * <p>Two defaults ship: {@link #ALWAYS} (explicit opt-in) and
 * {@link #NEVER} (abilities exclusive to a provider-owned bar).
 * Undeclared ids follow the server's {@code menu.undeclaredAbilities}
 * rule, <b>visible</b> by default - the never-guess reading (the
 * #116/#117 unlock rule extended) kept as the shipped value, with the
 * hide choice a pack makes explicitly: hiding would otherwise punish
 * providers that simply have not adopted the contract. Visibility
 * <em>hides</em>; unlock state <em>greys</em> (hotbar#135/#137) - two
 * states, two mechanics, never conflated.</p>
 */
public final class VisibilityHandlers {

    /**
     * The listing rule for one ability: whether the menu shows it. The
     * player is the menu's viewer (either side; null when no player
     * context exists - handlers must tolerate it).
     */
    @FunctionalInterface
    public interface VisibilityHandler {
        boolean visible(@Nullable Player player, ResourceLocation abilityId);
    }

    /** The always-visible default. */
    public static final VisibilityHandler ALWAYS = (player, abilityId) -> true;

    /** The never-visible default (provider-bar-exclusive abilities). */
    public static final VisibilityHandler NEVER = (player, abilityId) -> false;

    private static final Map<ResourceLocation, VisibilityHandler> HANDLERS =
            new ConcurrentHashMap<>();

    private VisibilityHandlers() {
    }

    /**
     * Declares an ability's menu visibility at mod construction.
     *
     * @throws IllegalArgumentException on null id or null handler
     * @throws IllegalStateException    on duplicate declaration (loud conflict)
     */
    public static void declare(ResourceLocation abilityId, VisibilityHandler handler) {
        if (abilityId == null) {
            throw new IllegalArgumentException("ability id must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException(
                    "visibility handler must not be null for " + abilityId);
        }
        VisibilityHandler previous = HANDLERS.putIfAbsent(abilityId, handler);
        if (previous != null) {
            throw new IllegalStateException("Duplicate visibility declaration for " + abilityId
                    + " - exactly one handler per ability (core#124)");
        }
    }

    public static boolean has(ResourceLocation abilityId) {
        return abilityId != null && HANDLERS.containsKey(abilityId);
    }

    /**
     * The menu's never-guess read: a declared handler answers, an
     * undeclared id follows the server's {@code menu.undeclaredAbilities}
     * rule (visible by default - unknown is never a reason to hide unless
     * the pack says so), and a null id is malformed input - nothing to
     * list.
     */
    public static boolean visible(@Nullable Player player, ResourceLocation abilityId) {
        if (abilityId == null) {
            return false;
        }
        VisibilityHandler handler = HANDLERS.get(abilityId);
        return handler != null
                ? handler.visible(player, abilityId)
                : visibleWhenUndeclared(CoreConfig.undeclaredAbilities());
    }

    /**
     * The undeclared-id half alone (the test seam - the config itself is
     * not loadable headless): everything except {@code HIDDEN} lists.
     */
    static boolean visibleWhenUndeclared(CoreConfig.UndeclaredVisibility rule) {
        return rule != CoreConfig.UndeclaredVisibility.HIDDEN;
    }

    /** Test isolation / future reload paths. */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && HANDLERS.remove(abilityId) != null;
    }
}
