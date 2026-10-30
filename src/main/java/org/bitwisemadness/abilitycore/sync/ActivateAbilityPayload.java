package org.bitwisemadness.abilitycore.sync;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.activation.ActivationPipeline;

/**
 * Serverbound activation request payload (REQ-CORE-02, REQ-CORE-08).
 *
 * <p>Carries the client's <em>intent</em> to activate an ability. The server
 * resolves the request through the {@link ActivationPipeline} and re-sends
 * the authoritative state on refusal - the client never holds activation
 * authority.</p>
 *
 * <p>Registered on the core's network channel with
 * {@code versioned("1").optional()} - version skew disconnects (verified:
 * {@code NetworkComponentNegotiator}), so codec changes require a version
 * bump shipped to both sides together (ADR-003).</p>
 *
 * @param abilityId   registry id of the ability to activate
 * @param source      activation source wire name (e.g. {@code direct_slot})
 * @param slotContext hotbar slot index, or -1 when not slot-bound
 * @param clientStamp advisory client timestamp (diagnostics only)
 */
public record ActivateAbilityPayload(
        String abilityId,
        String source,
        int slotContext,
        long clientStamp) implements CustomPacketPayload {

    public static final Type<ActivateAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "activate_ability"));

    public static final StreamCodec<ByteBuf, ActivateAbilityPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, ActivateAbilityPayload::abilityId,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, ActivateAbilityPayload::source,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ActivateAbilityPayload::slotContext,
            net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, ActivateAbilityPayload::clientStamp,
            ActivateAbilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler: resolves the request through the activation
     * pipeline. Malformed input (unknown id syntax, unknown source) is
     * fail-closed - the payload is silently dropped (no activation, no state
     * change, no error sent to the client for garbage input).
     */
    public static void handle(ActivateAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var request = org.bitwisemadness.abilitycore.api.ActivationRequest.fromWire(
                        payload.abilityId(), payload.source(), payload.slotContext(), payload.clientStamp());
                if (request != null) {
                    AbilityCore.PIPELINE.run(request, serverPlayer);
                }
                // null request = malformed input: dropped, no activation, no error sent
            }
        });
    }
}
