package org.bitwisemadness.tensuraabilitycore.activation;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraabilitycore.api.AbilityDefinition;
import org.bitwisemadness.tensuraabilitycore.api.ActivationOutcome;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.bitwisemadness.tensuraabilitycore.api.ActivationResult;
import org.bitwisemadness.tensuraabilitycore.api.TargetContext;
import org.bitwisemadness.tensuraabilitycore.registry.AbilityRegistries;
import org.slf4j.Logger;

/**
 * The ELIGIBILITY-phase target validation and resolution step
 * (REQ-CORE-06, registered first by {@code TensuraAbilityCore} so provider
 * eligibility rules run after targets are resolved).
 *
 * <p>Rules:</p>
 * <ol>
 *   <li><b>Accepted kinds gate:</b> if the definition declares
 *       {@code data.accepted_targets} (comma-separated kind names), a hint of
 *       any other kind is refused with {@code REFUSED_INVALID_TARGET} -
 *       fail-closed for kinds listed-but-invalid. <b>Absent key = no core
 *       kind gate</b>: the provider's native validation remains the
 *       authority (documented decision - core does not invent policy).</li>
 *   <li><b>Resolution:</b> {@code EntityHint} must resolve in the requester's
 *       level (resolved instance stashed under {@link #RESOLVED_ENTITY} so
 *       providers never re-resolve); {@code BlockHint}/{@code AreaHint}
 *       require a loaded chunk at the center; unresolvable -> refused.
 *       {@code None}/{@code Self} always pass resolution.</li>
 *   <li>Client-supplied hints are exactly this: hints. Providers still run
 *       their native targeting validation afterwards (core resolution does
 *       not replace it - it precedes it).</li>
 * </ol>
 *
 * <p>Wire-malformed targets never reach here: {@code TargetContext.fromWire}
 * fails closed at the request layer (pipeline sees no request).</p>
 */
public final class TargetValidationStep implements ActivationPipeline.Step {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** First eligibility step registered at mod construction. */
    public static final TargetValidationStep INSTANCE = new TargetValidationStep();

    /** Definition data key declaring accepted hint kinds (comma-separated). */
    public static final String ACCEPTED_TARGETS_KEY = "accepted_targets";

    /** Context key carrying the server-resolved (opaque) value for provider consumption. */
    public static final Object RESOLVED_ENTITY = new Object();

    private final TargetResolver resolver;

    public TargetValidationStep(TargetResolver resolver) {
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
    }

    private TargetValidationStep() {
        this(TargetResolver.DEFAULT);
    }

    @Override
    public Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player,
            ActivationContext context) {
        var definitions = AbilityRegistries.registry();
        if (definitions == null) {
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE));
        }
        AbilityDefinition definition = definitions.get(request.abilityId());
        if (definition == null) {
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE));
        }
        TargetContext target = request.target();
        TargetContext.Kind kind = TargetContext.kindOfNullable(target);

        // 1. accepted-kinds gate (absent key = provider remains the authority)
        String acceptedRaw = definition.data().get(ACCEPTED_TARGETS_KEY);
        if (acceptedRaw != null && !acceptedRaw.isBlank() && !accepts(acceptedRaw, kind)) {
            LOGGER.debug("Refusing {}: target kind {} not accepted (declared: {})",
                    request.abilityId(), kind, acceptedRaw);
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));
        }

        // 2. resolution
        if (target == null || target instanceof TargetContext.None
                || target instanceof TargetContext.Self) {
            return Optional.empty();
        }
        if (target instanceof TargetContext.EntityHint hint) {
            Object resolved = resolver.resolveEntity(hint.entityId(), player);
            if (resolved == null) {
                LOGGER.debug("Refusing {}: entity {} not resolvable for requester",
                        request.abilityId(), hint.entityId());
                return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));
            }
            context.put(RESOLVED_ENTITY, resolved);
            return Optional.empty();
        }
        if (target instanceof TargetContext.BlockHint hint) {
            return loaded(hint.pos(), player, request);
        }
        if (target instanceof TargetContext.AreaHint hint) {
            // center must be loaded; radius sanity is enforced by AreaHint's ctor
            return loaded(hint.center(), player, request);
        }
        // unknown TargetContext implementation = unverifiable -> fail closed
        LOGGER.error("Unknown TargetContext {} for {}", target.getClass().getName(), request.abilityId());
        return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));
    }

    private Optional<ActivationResult> loaded(net.minecraft.core.BlockPos pos, ServerPlayer player,
            ActivationRequest request) {
        try {
            if (!resolver.isLoaded(pos, player)) {
                LOGGER.debug("Refusing {}: position {} not loaded", request.abilityId(), pos);
                return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Refusing {}: position lookup failed ({})", request.abilityId(), e.toString());
            return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET));
        }
        return Optional.empty();
    }

    private static boolean accepts(String acceptedRaw, TargetContext.Kind kind) {
        for (String part : acceptedRaw.split(",")) {
            if (part.trim().equalsIgnoreCase(kind.name())) {
                return true;
            }
        }
        return false;
    }
}
