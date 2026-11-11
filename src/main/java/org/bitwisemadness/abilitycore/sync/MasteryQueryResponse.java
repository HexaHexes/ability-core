package org.bitwisemadness.abilitycore.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilitycore.mastery.MasteryLevels;
import org.jetbrains.annotations.Nullable;

/**
 * The mastery snapshot a query answers with (REQ-CORE-12): one entry per
 * stored target (kind, id, XP, derived level, in-level progress), sorted
 * for a stable UI, echoing the requested player so a client can correlate
 * replies. An offline player answers with the echo and empty entries.
 *
 * <p>The client fires {@link MasteryQueryAnsweredEvent} when this
 * arrives; core keeps no client-side copy (display-only, solicited-only
 * - docs/sync.md).</p>
 *
 * @param targetPlayer the queried player's UUID as echoed
 * @param entries      the stored targets (unmodifiable)
 */
public record MasteryQueryResponse(String targetPlayer, List<Entry> entries)
        implements CustomPacketPayload {

    /**
     * One stored target's snapshot.
     *
     * @param kind     {@code ABILITY} / {@code CATEGORY} / {@code SUBCATEGORY}
     * @param id       the target id as a string
     * @param xp       raw stored XP
     * @param level    the level the XP reaches on the current curve
     * @param progress the pool's 0..1 fill inside that level (the row
     *                 meter's bar - server-computed like {@code level},
     *                 never recomputed client-side)
     */
    public record Entry(String kind, String id, double xp, int level, double progress) {
    }

    public static final Type<MasteryQueryResponse> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "mastery_query_response"));

    /** Wire bound: entries are distinct targets, never near this many. */
    private static final int MAX_ENTRIES = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, MasteryQueryResponse> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(RegistryFriendlyByteBuf buf, MasteryQueryResponse value) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, value.targetPlayer());
                    buf.writeVarInt(value.entries().size());
                    for (Entry entry : value.entries()) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, entry.kind());
                        ByteBufCodecs.STRING_UTF8.encode(buf, entry.id());
                        ByteBufCodecs.DOUBLE.encode(buf, entry.xp());
                        buf.writeVarInt(entry.level());
                        ByteBufCodecs.DOUBLE.encode(buf, entry.progress());
                    }
                }

                @Override
                public MasteryQueryResponse decode(RegistryFriendlyByteBuf buf) {
                    String targetPlayer = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int count = buf.readVarInt();
                    if (count < 0 || count > MAX_ENTRIES) {
                        throw new IllegalArgumentException(
                                "mastery query response entry count out of range: " + count);
                    }
                    List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String kind = ByteBufCodecs.STRING_UTF8.decode(buf);
                        String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                        double xp = ByteBufCodecs.DOUBLE.decode(buf);
                        int level = buf.readVarInt();
                        double progress = ByteBufCodecs.DOUBLE.decode(buf);
                        entries.add(new Entry(kind, id, xp, level, progress));
                    }
                    return new MasteryQueryResponse(targetPlayer, List.copyOf(entries));
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Builds the answer for one requested player.
     *
     * @param targetPlayer the echoed request uuid (always present)
     * @param target       the online player, or null for empty entries
     * @return the snapshot
     */
    public static MasteryQueryResponse of(String targetPlayer, @Nullable ServerPlayer target) {
        if (target == null) {
            return new MasteryQueryResponse(targetPlayer, List.of());
        }
        var data = MasteryAttachments.data(target);
        List<Entry> entries = new ArrayList<>();
        data.entries().forEach((key, xp) -> {
            MasteryTarget parsed = MasteryTarget.parseKey(key);
            if (parsed != null) {
                entries.add(new Entry(parsed.kind().name(), parsed.id().toString(), xp,
                        MasteryLevels.level(xp), MasteryLevels.progress(xp)));
            }
        });
        entries.sort(Comparator.comparing(Entry::kind).thenComparing(Entry::id));
        return new MasteryQueryResponse(targetPlayer, List.copyOf(entries));
    }

    /** Client-side: hand the arrived snapshot to listeners (display code). */
    public static void handle(MasteryQueryResponse payload, IPayloadContext context) {
        context.enqueueWork(() -> NeoForge.EVENT_BUS.post(new MasteryQueryAnsweredEvent(payload)));
    }
}
