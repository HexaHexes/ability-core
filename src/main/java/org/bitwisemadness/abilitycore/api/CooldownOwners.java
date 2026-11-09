package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Per-ability cooldown ownership (REQ-CORE-05, ADR-002-style exclusivity).
 *
 * <p>Exactly one owner per ability, declared once: {@link Owner#NATIVE} means
 * the provider checks and starts cooldowns itself (the default - core does
 * nothing), {@link Owner#EXTERNAL} means core's {@link CooldownTracker} does,
 * with a mandatory positive duration (the <b>default fixed-cooldown handler</b>
 * for a whole ability), and {@link Owner#HANDLER} means core checks
 * pre-activation through a {@link CooldownHandler} while the domain starts
 * the cooldown itself (the per-spell/per-mode seam). One declaration per id,
 * duplicate or malformed declarations fail loudly - the dual-owner state is
 * unconstructible just like chargingModel exclusivity.</p>
 */
public final class CooldownOwners {

    public enum Owner {
        /** Provider's pipeline checks/starts cooldowns; core stays out. */
        NATIVE,
        /** Core checks pre-activation and starts post-activation via a tracker. */
        EXTERNAL,
        /** Core checks pre-activation through the handler; the domain starts. */
        HANDLER
    }

    /**
     * Read-through view of a provider's OWN native cooldown state (REQ-CORE-05
     * projection rule, core#16): core stores this function, never the state -
     * so the answer is the provider's by construction (exact parity), there is
     * no core-side copy to re-sync, and a {@code /reload} or re-learn is
     * visible on the next read. Server-side only: the client reads the
     * provider's own synced state through the overlay tuple, never here.
     */
    @FunctionalInterface
    public interface NativeProjection {
        long remainingTicks(ResourceLocation abilityId, @Nullable ServerPlayer player);
    }

    private record Entry(Owner owner, long durationTicks, @Nullable CooldownTracker tracker,
            @Nullable NativeProjection projection, @Nullable CooldownHandler handler) {
        private Entry {
            if (owner == null) {
                throw new IllegalArgumentException("owner must not be null");
            }
            if (owner == Owner.EXTERNAL && (tracker == null || durationTicks <= 0)) {
                throw new IllegalArgumentException(
                        "EXTERNAL cooldown requires a tracker and a positive duration (ticks), got " + durationTicks);
            }
            if (owner == Owner.NATIVE && tracker != null) {
                throw new IllegalArgumentException(
                        "NATIVE cooldown forbids a core tracker - the provider owns its cooldown state");
            }
            if (owner == Owner.EXTERNAL && projection != null) {
                throw new IllegalArgumentException(
                        "EXTERNAL cooldown forbids a projection - core's tracker is the answer source");
            }
            if (owner == Owner.HANDLER) {
                if (handler == null) {
                    throw new IllegalArgumentException(
                            "HANDLER cooldown requires a handler - it is the gate and the answer source");
                }
                if (tracker != null || projection != null || durationTicks != 0L) {
                    throw new IllegalArgumentException(
                            "HANDLER cooldown forbids a tracker, projection, or duration - the handler answers alone");
                }
            } else if (handler != null) {
                throw new IllegalArgumentException(
                        owner + " cooldown forbids a handler - declareHandler owns that state");
            }
        }
    }

    private static final Map<ResourceLocation, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private CooldownOwners() {
    }

    /** Default semantics: providers own their cooldowns (most common case). */
    public static void declareNative(ResourceLocation abilityId) {
        declare(abilityId, new Entry(Owner.NATIVE, 0L, null, null, null));
    }

    /**
     * NATIVE with a read-through projection: core's contract reports this
     * provider's remaining exactly (core#16 AC1 parity) while the provider's
     * pipeline stays the only gate and start site. The projection is a
     * function, not state - core never copies or re-syncs the answer.
     */
    public static void declareNative(ResourceLocation abilityId, NativeProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("projection must not be null - use declareNative(id) without one");
        }
        declare(abilityId, new Entry(Owner.NATIVE, 0L, null, projection, null));
    }

    /** Core-tracked cooldown with the built-in store (persistence optional). */
    public static void declareExternal(ResourceLocation abilityId, long durationTicks) {
        declare(abilityId, new Entry(Owner.EXTERNAL, durationTicks, CoreCooldownTracker.INSTANCE, null, null));
    }

    /** Core-tracked cooldown with a provider-supplied tracker (e.g. for persisted state). */
    public static void declareExternal(ResourceLocation abilityId, long durationTicks, CooldownTracker tracker) {
        declare(abilityId, new Entry(Owner.EXTERNAL, durationTicks, tracker, null, null));
    }

    /**
     * Request-aware cooldown gate (REQ-CORE-05): core refuses while the
     * handler reports remaining &gt; 0 - asked with the whole
     * {@link ActivationRequest}, so per-spell and per-mode cooldowns are
     * answerable - and never starts the cooldown: the domain starts it at
     * its native moment (the single-start rule). The handler IS the answer
     * source, so no duration, tracker, or projection can be declared
     * beside it.
     */
    public static void declareHandler(ResourceLocation abilityId, CooldownHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException(
                    "handler must not be null - declareHandler(id) without one gates nothing");
        }
        declare(abilityId, new Entry(Owner.HANDLER, 0L, null, null, handler));
    }

    private static void declare(ResourceLocation abilityId, Entry entry) {
        if (abilityId == null) {
            throw new IllegalArgumentException("abilityId must not be null");
        }
        Entry previous = ENTRIES.putIfAbsent(abilityId, entry);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate cooldown declaration for " + abilityId + " - exactly one owner per ability (REQ-CORE-05)");
        }
    }

    /** Fail-closed default: unregistered ids are treated as NATIVE (gates refuse first). */
    public static Owner ownerOf(ResourceLocation abilityId) {
        Entry entry = abilityId == null ? null : ENTRIES.get(abilityId);
        return entry == null ? Owner.NATIVE : entry.owner();
    }

    public static Optional<CooldownTracker> trackerFor(ResourceLocation abilityId) {
        Entry entry = abilityId == null ? null : ENTRIES.get(abilityId);
        return entry == null || entry.owner() != Owner.EXTERNAL
                ? Optional.empty()
                : Optional.ofNullable(entry.tracker());
    }

    /**
     * The registered native projection, or null (NATIVE without one,
     * EXTERNAL, HANDLER, or undeclared) - {@link CooldownContract} maps null
     * to unknown.
     */
    @Nullable
    public static NativeProjection projectionFor(ResourceLocation abilityId) {
        Entry entry = abilityId == null ? null : ENTRIES.get(abilityId);
        return entry == null || entry.owner() != Owner.NATIVE ? null : entry.projection();
    }

    /**
     * The registered request-aware handler, or null (any non-HANDLER owner,
     * or undeclared). The COOLDOWN step consults it only for HANDLER-owned
     * ids; a null there (unconstructible via declarations, defence in depth)
     * refuses fail-closed.
     */
    @Nullable
    public static CooldownHandler handlerFor(ResourceLocation abilityId) {
        Entry entry = abilityId == null ? null : ENTRIES.get(abilityId);
        return entry == null || entry.owner() != Owner.HANDLER ? null : entry.handler();
    }

    /** Declared EXTERNAL duration in ticks, or 0 for NATIVE/HANDLER/undeclared. */
    public static long declaredDurationTicks(ResourceLocation abilityId) {
        Entry entry = abilityId == null ? null : ENTRIES.get(abilityId);
        return entry == null ? 0L : entry.durationTicks();
    }

    /** Test isolation / future reload paths. */
    public static boolean remove(ResourceLocation abilityId) {
        return abilityId != null && ENTRIES.remove(abilityId) != null;
    }

    /**
     * Built-in, in-memory EXTERNAL cooldown store: player UUID -> ability id
     * -> expiry game-time tick. Persisted only when the server turns on
     * {@code gameplay.persistExternalCooldowns} (default off keeps the
     * original in-memory-only decision): the expiry is written beside the
     * player the moment a cooldown starts and read back at login, so a
     * rejoin or restart cannot reset one. Game time is absolute, so a saved
     * expiry reads correctly after a restart.
     */
    public static final class CoreCooldownTracker implements CooldownTracker {

        public static final CoreCooldownTracker INSTANCE = new CoreCooldownTracker();

        /** The player-NBT key holding persisted EXTERNAL cooldowns. */
        static final String PERSIST_TAG = "ability_core:cooldowns";

        private final Map<UUID, Map<ResourceLocation, Long>> expiryByPlayer = new ConcurrentHashMap<>();

        private CoreCooldownTracker() {
        }

        @Override
        public long remainingTicks(ResourceLocation abilityId, ServerPlayer player) {
            Map<ResourceLocation, Long> byAbility = expiryByPlayer.get(player.getUUID());
            if (byAbility == null) {
                return 0L;
            }
            Long expiry = byAbility.get(abilityId);
            if (expiry == null) {
                return 0L;
            }
            long remaining = expiry - player.level().getGameTime();
            if (remaining <= 0) {
                byAbility.remove(abilityId); // lazy cleanup
                return 0L;
            }
            return remaining;
        }

        @Override
        public void start(ResourceLocation abilityId, ServerPlayer player, long durationTicks) {
            long expiry = player.level().getGameTime() + durationTicks;
            expiryByPlayer
                    .computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                    .put(abilityId, expiry);
            if (CoreConfig.persistExternalCooldowns()) {
                persist(player, abilityId, expiry);
            }
        }

        /**
         * Loads this player's persisted cooldowns at login (config-gated,
         * never throws): only entries still in the future survive, and the
         * later of a saved and an already-known expiry wins - a restart
         * can only ever lengthen what memory already holds, never shorten
         * it. An unreadable tag costs the entries, never the join.
         */
        public void restore(ServerPlayer player) {
            if (!CoreConfig.persistExternalCooldowns()) {
                return;
            }
            try {
                CompoundTag saved = player.getPersistentData().getCompound(PERSIST_TAG);
                if (saved.isEmpty()) {
                    return;
                }
                long now = player.level().getGameTime();
                Map<ResourceLocation, Long> known = expiryByPlayer
                        .computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
                for (String key : saved.getAllKeys()) {
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    if (id == null) {
                        continue;
                    }
                    long expiry = saved.getLong(key);
                    if (expiry > now) {
                        known.merge(id, expiry, Math::max);
                    }
                }
            } catch (RuntimeException unreadable) {
                LOGGER.debug("Could not restore persisted cooldowns for {} ({})",
                        player.getName().getString(), unreadable.toString());
            }
        }

        /** Writes one cooldown beside the player (persist-on-start). */
        private void persist(ServerPlayer player, ResourceLocation abilityId, long expiry) {
            try {
                CompoundTag root = player.getPersistentData();
                CompoundTag saved = root.getCompound(PERSIST_TAG);
                saved.putLong(abilityId.toString(), expiry);
                root.put(PERSIST_TAG, saved);
            } catch (RuntimeException unwritable) {
                // diagnostics only: a failed write costs persistence, never a cast
                LOGGER.debug("Could not persist cooldown {} for {} ({})", abilityId,
                        player.getName().getString(), unwritable.toString());
            }
        }

        /** Test isolation. */
        public void clear() {
            expiryByPlayer.clear();
        }
    }
}
