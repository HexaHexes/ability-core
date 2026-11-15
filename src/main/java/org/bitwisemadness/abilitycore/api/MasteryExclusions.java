package org.bitwisemadness.abilitycore.api;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Domain-declared default exclusions from the mastery system - the
 * blacklist's domain half (owner directive 2026-11-15). Core's
 * {@code mastery.blacklist} is an operator-owned SERVER config whose
 * default can never name another mod's ids, so a provider declares its
 * own "never participates" set here instead: ability-tensura registers
 * its resistances and nullifications, whose passive shapes would
 * otherwise declare COST/POWER/DURATION like any art.
 *
 * <p>Every {@code CoreConfig.masteryBlacklisted} read is the union of
 * the config entries and this set - a declared id behaves exactly like
 * a listed one at every award, effect and query site (no XP, inert
 * multipliers, no pane entry), while the config keeps its additive
 * operator role. Declarations are idempotent and live-read; a domain
 * default is unconditional (not an operator override), so runtime
 * un-declare exists only as {@link #remove} for test isolation.</p>
 */
public final class MasteryExclusions {

    /** The declared ids (setup-time writes, game-thread reads). */
    private static final Set<ResourceLocation> EXCLUDED = ConcurrentHashMap.newKeySet();

    private MasteryExclusions() {
    }

    /** Declares one id as excluded from every mastery read (idempotent). */
    public static void declare(ResourceLocation id) {
        if (id != null) {
            EXCLUDED.add(id);
        }
    }

    /** Declares a batch (e.g. one domain's whole category tag). */
    public static void declareAll(Iterable<? extends ResourceLocation> ids) {
        Objects.requireNonNull(ids, "ids");
        for (ResourceLocation id : ids) {
            declare(id);
        }
    }

    /**
     * Whether the domain defaults exclude this id (null never matches).
     * Merged into the blacklist read by core itself - callers keep
     * asking the one question, {@code masteryBlacklisted}.
     */
    public static boolean excluded(@Nullable ResourceLocation id) {
        return id != null && EXCLUDED.contains(id);
    }

    /** Undeclares one id - test isolation, never a runtime override. */
    public static void remove(ResourceLocation id) {
        if (id != null) {
            EXCLUDED.remove(id);
        }
    }
}
