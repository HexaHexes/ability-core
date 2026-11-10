package org.bitwisemadness.abilitycore.mastery;

import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.bitwisemadness.abilitycore.AbilityCore;

/**
 * The per-player mastery attachment (REQ-CORE-12): persisted with death
 * copy-on-death (progression survives), registered through a deferred
 * register on the mod bus exactly like hotbar's bar-state attachment.
 * Server-authoritative; clients read it only through the explicit query
 * payload pair, never a push.
 */
public final class MasteryAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AbilityCore.MOD_ID);

    /**
     * The mastery XP store: per-player, persisted, copied on death.
     * Schema v1, flat key/xp pairs (see {@link MasteryData}).
     */
    public static final Supplier<AttachmentType<MasteryData>> MASTERY =
            ATTACHMENT_TYPES.register("mastery", () -> AttachmentType.builder(MasteryData::new)
                    .serialize(new MasteryDataSerializer())
                    .copyOnDeath()
                    .build());

    /** Call from the mod constructor to register on the mod bus. */
    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    /** The player's store (creates the attachment on first read). */
    public static MasteryData data(ServerPlayer player) {
        return player.getData(MASTERY);
    }

    /** NBT bridge: {@link MasteryData#save()} / {@link MasteryData#load}. */
    private static final class MasteryDataSerializer
            implements IAttachmentSerializer<CompoundTag, MasteryData> {

        @Override
        public MasteryData read(IAttachmentHolder holder, CompoundTag tag,
                HolderLookup.Provider provider) {
            return MasteryData.load(tag);
        }

        @Override
        public CompoundTag write(MasteryData attachment, HolderLookup.Provider provider) {
            return attachment.save();
        }
    }

    private MasteryAttachments() {
    }
}
