package org.bitwisemadness.abilitycore.sync;

import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.TargetContext;

/**
 * Serverbound activation request payload (REQ-CORE-02, REQ-CORE-08,
 * extended by core#69 to carry a target hint).
 *
 * <p>Carries the client's <em>intent</em> to activate an ability, plus an
 * optional <b>target hint</b> in {@link TargetContext}'s wire vocabulary:
 * a kind string plus flat {@code key=value} params joined with commas
 * (the vocabulary's values - uuids, integers, doubles - never contain a
 * comma or equals sign). An absent hint (blank kind) builds exactly the
 * request today's four-arg path built - {@link TargetContext.None}; a
 * <b>declared but unparseable</b> hint builds <b>no request at all</b>
 * (the five-arg {@code fromWire} rule: a bad hint never becomes a
 * default). The server resolves the hint in {@code TargetValidationStep}
 * before any provider sees it - clients never carry authority.</p>
 *
 * <p>Registered on the core's network channel with
 * {@code versioned("2").optional()} - version skew disconnects (verified:
 * {@code NetworkComponentNegotiator}), so codec changes require a version
 * bump shipped to both sides together (ADR-003). This record's growth from
 * four to six fields <em>is</em> that bump: {@code 1 -> 2}, hotbar#94
 * ships the populated side.</p>
 *
 * @param abilityId    registry id of the ability to activate
 * @param source       activation source wire name (e.g. {@code direct_slot})
 * @param slotContext  hotbar slot index, or -1 when not slot-bound
 * @param clientStamp  advisory client timestamp (diagnostics only)
 * @param targetKind   {@link TargetContext} kind, or blank for no hint
 * @param targetParams flat {@code key=value} params for the kind, or blank
 */
public record ActivateAbilityPayload(
        String abilityId,
        String source,
        int slotContext,
        long clientStamp,
        String targetKind,
        String targetParams) implements CustomPacketPayload {

    public static final Type<ActivateAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "activate_ability"));

    public static final StreamCodec<ByteBuf, ActivateAbilityPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, ActivateAbilityPayload::abilityId,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, ActivateAbilityPayload::source,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ActivateAbilityPayload::slotContext,
            net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, ActivateAbilityPayload::clientStamp,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, ActivateAbilityPayload::targetKind,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, ActivateAbilityPayload::targetParams,
            ActivateAbilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Builds the pipeline request from the wire form - the testable half of
     * the handler (core#69 fixtures).
     *
     * @param payload the wire payload
     * @return the request, or <b>null</b> when anything is malformed: an
     *         unknown id syntax, an unknown source, or a declared target
     *         that does not parse - the caller drops such payloads silently
     *         (no activation, no state change, no error sent)
     */
    public static ActivationRequest toRequest(ActivateAbilityPayload payload) {
        if (payload.targetKind() == null || payload.targetKind().isBlank()) {
            // Absent hint: byte-for-byte the behaviour before core#69.
            return ActivationRequest.fromWire(payload.abilityId(), payload.source(),
                    payload.slotContext(), payload.clientStamp());
        }
        Map<String, String> params = parseParams(payload.targetParams());
        if (params == null) {
            return null; // structurally malformed params: declared but unparseable
        }
        Optional<TargetContext> target = TargetContext.fromWire(payload.targetKind(), params);
        if (target.isEmpty()) {
            return null; // declared-but-unparseable target never becomes a default
        }
        return ActivationRequest.fromWire(payload.abilityId(), payload.source(),
                payload.slotContext(), payload.clientStamp(), target.get());
    }

    /**
     * The hint's flat params: {@code key=value} pairs joined with commas.
     * Structural malformation (a pair without {@code =}) yields null so the
     * request is dropped; value parsing is {@link TargetContext}'s
     * fail-closed job.
     */
    private static Map<String, String> parseParams(String flat) {
        if (flat == null || flat.isBlank()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : flat.split(",")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                return null;
            }
            out.put(pair.substring(0, separator).trim(), pair.substring(separator + 1).trim());
        }
        return out;
    }

    /**
     * Server-side handler: resolves the request through the activation
     * pipeline. Malformed input (unknown id syntax, unknown source, a bad
     * target hint) is fail-closed - the payload is silently dropped (no
     * activation, no state change, no error sent to the client for garbage
     * input).
     */
    public static void handle(ActivateAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var request = toRequest(payload);
                if (request != null) {
                    AbilityCore.PIPELINE.run(request, serverPlayer);
                }
                // null request = malformed input: dropped, no activation, no error sent
            }
        });
    }
}
