package org.bitwisemadness.tensuraabilitycore.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Per-ability cooldown ownership (REQ-CORE-05, ADR-002-style exclusivity).
 *
 * <p>Exactly one owner per ability, declared once: {@link Owner#NATIVE} means
 * the provider checks and starts cooldowns itself (the default - core does
 * nothing), {@link Owner#EXTERNAL} means core's {@link CooldownTracker} does,
 * with a mandatory positive duration. One declaration per id, duplicate or
 * malformed declarations fail loudly - the dual-owner state is unconstructible
 * just like chargingModel exclusivity.</p>
 */
public final class CooldownOwners {

    public enum Owner {
        /** Provider's pipeline checks/starts cooldowns; core stays out. */
        NATIVE,
        /** Core checks pre-activation and starts post-activation via a tracker. */
        EXTERNAL
    }

    private record Entry(Owner owner, long durationTicks, @Nullable CooldownTracker tracker) {
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
        }
    }

    private static final Map<ResourceLocation, Entry> ENTRIES = new ConcurrentHashMap<>();

    private CooldownOwners() {
    }

    /** Default semantics: providers own their cooldowns (most common case). */
    public static void declareNative(ResourceLocation abilityId) {
        declare(abilityId, new Entry(Owner.NATIVE, 0L, null));
    }

    /** Core-tracked cooldown with the built-in non-persistent store. */
    public static void declareExternal(ResourceLocation abilityId, long durationTicks) {
        declare(abilityId, new Entry(Owner.EXTERNAL, durationTicks, CoreCooldownTracker.INSTANCE));
    }

    /** Core-tracked cooldown with a provider-supplied tracker (e.g. for persisted state). */
    public static void declareExternal(ResourceLocation abilityId, long durationTicks, CooldownTracker tracker) {
        declare(abilityId, new Entry(Owner.EXTERNAL, durationTicks, tracker));
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

    /** Declared EXTERNAL duration in ticks, or 0 for NATIVE/undeclared. */
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
     * -> expiry game-time tick. Not persisted (documented decision above).
     */
    public static final class CoreCooldownTracker implements CooldownTracker {

        public static final CoreCooldownTracker INSTANCE = new CoreCooldownTracker();

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
            expiryByPlayer
                    .computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                    .put(abilityId, player.level().getGameTime() + durationTicks);
        }

        /** Test isolation. */
        public void clear() {
            expiryByPlayer.clear();
        }
    }
}
