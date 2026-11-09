package org.bitwisemadness.abilitycore.sync;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.TargetContext;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.slf4j.Logger;

/**
 * Serverbound activation request payload (REQ-CORE-02, REQ-CORE-08,
 * extended by core#69 to carry a target hint and by issue #113 to carry
 * the mode context).
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
 * <p>The {@code mode} string (issue #113) is the mode the activating slot
 * is set to - blank means none, and a non-blank value becomes the
 * request's {@link ActivationRequest#modeContext()} advisory context
 * (providers validate it against their own declared modes, exactly like
 * the target hint). It rides the wire because the client owns the slot's
 * stored setting and core never reads consumer state.</p>
 *
 * <p>Registered on the core's network channel with
 * {@code versioned("3").optional()} - version skew disconnects (verified:
 * {@code NetworkComponentNegotiator}), so codec changes require a version
 * bump shipped to both sides together (ADR-003). This record's growth from
 * four to seven fields spans those bumps: {@code 1 -> 2} (target hint,
 * hotbar#94) and {@code 2 -> 3} (mode context, issue #113).</p>
 *
 * @param abilityId    registry id of the ability to activate
 * @param source       activation source wire name (e.g. {@code direct_slot})
 * @param slotContext  hotbar slot index, or -1 when not slot-bound
 * @param clientStamp  advisory client timestamp (diagnostics only)
 * @param targetKind   {@link TargetContext} kind, or blank for no hint
 * @param targetParams flat {@code key=value} params for the kind, or blank
 * @param mode         the slot's mode setting (issue #113), or blank for none
 */
public record ActivateAbilityPayload(
        String abilityId,
        String source,
        int slotContext,
        long clientStamp,
        String targetKind,
        String targetParams,
        String mode) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Layer-1 safety clamp (never config - {@code docs/config.md}): a
     * target hint longer than this is structurally garbage, since the
     * vocabulary holds uuids, integers and doubles. Drops fail-closed
     * before it is parsed.
     */
    static final int MAX_TARGET_PARAM_CHARS = 512;

    /** One rate window, in milliseconds. */
    private static final long WINDOW_MILLIS = 1000L;

    /** Windows kept before stale ones are swept (bounded memory). */
    private static final int MAX_WINDOWS = 512;

    /** Once-per-key cap: the malformed set clears when it reaches this size. */
    private static final int MAX_LOGGED_KEYS = 1024;

    /** Per-player activation windows ({@code network.activationRatePerSecond}). */
    private static final Map<UUID, long[]> RATE_WINDOWS = new ConcurrentHashMap<>();

    /** The bounded once-per-key set for malformed-payload logging. */
    private static final Set<String> LOGGED_MALFORMED = ConcurrentHashMap.newKeySet();

    public static final Type<ActivateAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AbilityCore.MOD_ID, "activate_ability"));

    // 7 fields exceeds StreamCodec.composite's six-pair overloads (issue #113),
    // so the wire order is written out: record order, field for field.
    public static final StreamCodec<ByteBuf, ActivateAbilityPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, ActivateAbilityPayload value) {
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buf, value.abilityId());
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buf, value.source());
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, value.slotContext());
            net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.encode(buf, value.clientStamp());
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buf, value.targetKind());
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buf, value.targetParams());
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buf, value.mode());
        }

        @Override
        public ActivateAbilityPayload decode(ByteBuf buf) {
            return new ActivateAbilityPayload(
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf),
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf),
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(buf),
                    net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.decode(buf),
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf),
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf),
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf));
        }
    };

    /** Pre-mode construction (issue #113): blank mode = no mode context. */
    public ActivateAbilityPayload(String abilityId, String source, int slotContext,
            long clientStamp, String targetKind, String targetParams) {
        this(abilityId, source, slotContext, clientStamp, targetKind, targetParams, "");
    }

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
        ActivationRequest request;
        if (payload.targetKind() == null || payload.targetKind().isBlank()) {
            // Absent hint: byte-for-byte the behaviour before core#69.
            request = ActivationRequest.fromWire(payload.abilityId(), payload.source(),
                    payload.slotContext(), payload.clientStamp());
        } else {
            Map<String, String> params = parseParams(payload.targetParams());
            if (params == null) {
                return null; // structurally malformed params: declared but unparseable
            }
            Optional<TargetContext> target = TargetContext.fromWire(payload.targetKind(), params);
            if (target.isEmpty()) {
                return null; // declared-but-unparseable target never becomes a default
            }
            request = ActivationRequest.fromWire(payload.abilityId(), payload.source(),
                    payload.slotContext(), payload.clientStamp(), target.get());
        }
        if (request != null && payload.mode() != null && !payload.mode().isBlank()) {
            request = request.withMode(payload.mode());
        }
        return request;
    }

    /**
     * The hint's flat params: {@code key=value} pairs joined with commas.
     * Structural malformation (a pair without {@code =}) yields null so the
     * request is dropped; value parsing is {@link TargetContext}'s
     * fail-closed job. The length is a <b>layer-1 code clamp</b> (never
     * config - {@code docs/config.md}): the vocabulary's values are uuids,
     * integers and doubles, so anything past {@value #MAX_TARGET_PARAM_CHARS}
     * characters is structurally garbage and drops before it is parsed.
     */
    private static Map<String, String> parseParams(String flat) {
        if (flat == null || flat.isBlank()) {
            return Map.of();
        }
        if (flat.length() > MAX_TARGET_PARAM_CHARS) {
            return null;
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
     * input) and logged under {@code diagnostics.malformedPayloadLog}. The
     * server's per-player ceiling ({@code network.activationRatePerSecond})
     * drops over-budget payloads before anything is parsed.
     */
    public static void handle(ActivateAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (!allowActivation(serverPlayer)) {
                    return; // over the ceiling: dropped, nothing parsed
                }
                var request = toRequest(payload);
                if (request != null) {
                    AbilityCore.PIPELINE.run(request, serverPlayer);
                    return;
                }
                // null request = malformed input: dropped, no activation,
                // no error sent - and a line under the server's log mode
                logMalformed(payload);
            }
        });
    }

    /**
     * The per-player activation ceiling (network.activationRatePerSecond):
     * a fixed one-second window per player; 0 (the default) is unlimited
     * and never touches the map. Rejected attempts still count, so spam
     * cannot buy its way past the window, and stale windows are swept
     * once the map grows past its bound (departed players never pile up).
     */
    private static boolean allowActivation(ServerPlayer player) {
        int perSecond = CoreConfig.activationRatePerSecond();
        if (perSecond <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long[] window = RATE_WINDOWS.compute(player.getUUID(),
                (id, current) -> current == null || now - current[0] >= WINDOW_MILLIS
                        ? new long[] {now, 1L}
                        : new long[] {current[0], current[1] + 1L});
        if (RATE_WINDOWS.size() > MAX_WINDOWS) {
            RATE_WINDOWS.entrySet()
                    .removeIf(entry -> now - entry.getValue()[0] >= WINDOW_MILLIS);
        }
        return window[1] <= perSecond;
    }

    /**
     * The malformed-payload line under {@code diagnostics.malformedPayloadLog}
     * (the refusal mode's shape): {@code off} silences, {@code every} logs
     * each drop, the default {@code once-per-key} suppresses repeats per
     * payload signature until the bounded set clears. The payload itself is
     * never echoed in full - it is, by definition, garbage.
     */
    private static void logMalformed(ActivateAbilityPayload payload) {
        CoreConfig.RefusalLogMode mode = CoreConfig.malformedPayloadLog();
        if (mode == CoreConfig.RefusalLogMode.OFF) {
            return;
        }
        String key = payload.abilityId() + "|" + payload.source() + "|" + payload.targetKind();
        if (mode == CoreConfig.RefusalLogMode.EVERY || addMalformedKey(key)) {
            LOGGER.debug("Dropping malformed activation payload (id '{}', source '{}',"
                            + " target kind '{}') - no request built",
                    payload.abilityId(), payload.source(), payload.targetKind());
        }
    }

    /** Bounded once-per-key set: clears wholesale when full, then records. */
    private static boolean addMalformedKey(String key) {
        if (LOGGED_MALFORMED.size() >= MAX_LOGGED_KEYS) {
            LOGGED_MALFORMED.clear();
        }
        return LOGGED_MALFORMED.add(key);
    }
}
