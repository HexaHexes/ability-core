package org.bitwisemadness.abilitycore.sync;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.slf4j.Logger;

/**
 * The explicit mastery query (REQ-CORE-12): a client asks for one
 * player's mastery snapshot and the server answers with
 * {@link MasteryQueryResponse}. The pair is core's only clientbound
 * exchange and it is <b>solicited</b> - core never pushes game data
 * (docs/sync.md). Display-only: an offline or unknown player answers
 * with empty entries, never an error.
 *
 * <p>Additive payload types do not bump the channel version - only a
 * codec change to a registered payload does (docs/sync.md's checklist).
 * A client that predates this type simply never sends the request.</p>
 *
 * @param targetPlayer the queried player's UUID as a string
 */
public record MasteryQueryRequest(String targetPlayer) implements CustomPacketPayload {

    public static final Type<MasteryQueryRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "mastery_query_request"));

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final StreamCodec<RegistryFriendlyByteBuf, MasteryQueryRequest> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MasteryQueryRequest::targetPlayer,
                    MasteryQueryRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client-side send helper: request {@code target}'s snapshot. */
    public static void sendToServer(UUID target) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new MasteryQueryRequest(target.toString()));
    }

    public static void handle(MasteryQueryRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            ServerPlayer target;
            try {
                target = serverPlayer.server.getPlayerList()
                        .getPlayer(UUID.fromString(payload.targetPlayer()));
            } catch (IllegalArgumentException e) {
                LOGGER.error("Malformed mastery query target '{}'", payload.targetPlayer());
                return;
            }
            context.reply(MasteryQueryResponse.of(payload.targetPlayer(), target));
        });
    }
}
