package org.bitwisemadness.abilitycore.sync;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.slf4j.Logger;

/**
 * Serverbound release request payload - the key-up half of
 * {@link ActivateAbilityPayload} (owner brief: native-parity release
 * channel; without it, press-only clients start ARR holds that only the
 * provider's own timeout can end, and release-fired arts never fire).
 *
 * <p>Carries the client's intent to <em>stop</em> what a prior activation
 * of the same id started. It is deliberately the activation shape minus
 * the target hint: the crosshair may have moved while the key was held,
 * and a stop must never fail on targeting. The {@code mode} string is the
 * same advisory context the press carried (issue #113's vocabulary) -
 * providers that key holds by mode can use it, providers that resolve
 * holds from their own state (the tensura slice) ignore it.</p>
 *
 * <p>Registered as an <b>additive</b> type on the existing versioned
 * channel (docs/sync.md: additive types do not bump N - the codec is new,
 * nothing existing changed), shares the per-player activation ceiling so
 * releases cannot be spammed past {@code network.activationRatePerSecond},
 * and routes to {@code AbilityCore.PIPELINE.release} - past every policy
 * phase, because a stop is never refused.</p>
 *
 * @param abilityId   registry id of the ability to stop
 * @param source      activation source wire name of the original press
 * @param slotContext hotbar slot index, or -1 when not slot-bound
 * @param clientStamp advisory client timestamp (diagnostics only)
 * @param mode        the slot's mode setting, or blank for none
 */
public record ReleaseAbilityPayload(
        String abilityId,
        String source,
        int slotContext,
        long clientStamp,
        String mode) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<ReleaseAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "release_ability"));

    /** Five fields fit StreamCodec.composite's overload family. */
    public static final StreamCodec<ByteBuf, ReleaseAbilityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                    ReleaseAbilityPayload::abilityId,
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                    ReleaseAbilityPayload::source,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    ReleaseAbilityPayload::slotContext,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,
                    ReleaseAbilityPayload::clientStamp,
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                    ReleaseAbilityPayload::mode,
                    ReleaseAbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * The pipeline request from the wire form - fail-closed like the
     * activation half: an unknown id syntax or source builds no request
     * at all (the caller drops such payloads silently), and a blank mode
     * means no mode context (never an empty one).
     */
    public static ActivationRequest toRequest(ReleaseAbilityPayload payload) {
        ActivationRequest request = ActivationRequest.fromWire(payload.abilityId(), payload.source(),
                payload.slotContext(), payload.clientStamp());
        if (request != null && payload.mode() != null && !payload.mode().isBlank()) {
            request = request.withMode(payload.mode());
        }
        return request;
    }

    /**
     * Server-side handler: resolves through {@code PIPELINE.release}.
     * Shares the activation ceiling (releases are 1:1 with presses, so a
     * configured budget counts both directions - a spammer cannot buy
     * extra presses with release noise), and malformed input drops
     * silently under the same {@code diagnostics.malformedPayloadLog}
     * mode as the activation half.
     */
    public static void handle(ReleaseAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (!ActivateAbilityPayload.allowActivation(serverPlayer)) {
                    return; // over the ceiling: dropped, nothing parsed
                }
                var request = toRequest(payload);
                if (request != null) {
                    AbilityCore.PIPELINE.release(request, serverPlayer);
                    return;
                }
                logMalformed(payload);
            }
        });
    }

    /** The malformed-drop line, same log-mode contract as the press half. */
    private static void logMalformed(ReleaseAbilityPayload payload) {
        CoreConfig.RefusalLogMode mode = CoreConfig.malformedPayloadLog();
        if (mode == CoreConfig.RefusalLogMode.OFF) {
            return;
        }
        LOGGER.debug("Dropping malformed release payload (id '{}', source '{}') - no request built",
                payload.abilityId(), payload.source());
    }
}
