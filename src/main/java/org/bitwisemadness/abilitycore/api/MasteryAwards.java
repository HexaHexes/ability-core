package org.bitwisemadness.abilitycore.api;

import com.mojang.logging.LogUtils;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilitycore.mastery.MasteryRouting;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The experience entry points (REQ-CORE-12). {@link #award} routes a
 * grant through the declared ability's share list (self keeps the
 * remainder); {@link #awardTo} writes one target directly (quest credit,
 * discovery - literal amounts, no shares, no global multiplier). Both are
 * inert when {@code mastery.enabled} is off, and {@code award} silently
 * skips abilities that opted out or did not declare the trigger.
 *
 * <p>Core fires {@link MasteryTrigger#ACTIVATION} from the pipeline's
 * post-activation tail; domains fire {@code HIT}/{@code KILL}/{@code
 * CUSTOM} here with the same call. Server-side only.</p>
 */
public final class MasteryAwards {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MasteryAwards() {
    }

    /**
     * Grants one use of {@code source} under {@code trigger}, using the
     * spec's gain handler (or base gain), the global gain multiplier, and
     * the spec's share routing. A no-op for: a null player, the system
     * disabled, an undeclared ability, an undeclared trigger, or a
     * non-positive/non-finite gain.
     */
    public static void award(@Nullable ServerPlayer player, ResourceLocation source,
            MasteryTrigger trigger) {
        awardResolved(player, source, trigger, Double.NaN);
    }

    /**
     * As {@link #award} but with an explicit raw amount (a domain that
     * scaled the gain itself - damage-based XP - skips the gain handler
     * and base gain; routing and the global multiplier still apply).
     */
    public static void award(@Nullable ServerPlayer player, ResourceLocation source,
            MasteryTrigger trigger, double rawAmount) {
        if (!Double.isFinite(rawAmount) || rawAmount < 0.0) {
            LOGGER.error("Mastery award to {} under {} rejected: raw amount {}",
                    source, trigger, rawAmount);
            return;
        }
        awardResolved(player, source, trigger, rawAmount);
    }

    /**
     * Writes {@code amount} to one target directly: no trigger filter, no
     * share split, no global multiplier - a literal grant. Inert when the
     * system is disabled or the amount is not finite and positive.
     */
    public static void awardTo(@Nullable ServerPlayer player, MasteryTarget target,
            double amount) {
        if (player == null || target == null || !CoreConfig.masteryEnabled()) {
            return;
        }
        if (!Double.isFinite(amount) || amount <= 0.0) {
            LOGGER.error("Mastery awardTo {} rejected: amount {}", target, amount);
            return;
        }
        MasteryAttachments.data(player).addXp(target.key(), amount);
    }

    /** NaN means "resolve from the spec"; every other finite value is explicit. */
    private static void awardResolved(@Nullable ServerPlayer player, ResourceLocation source,
            MasteryTrigger trigger, double rawAmount) {
        if (player == null || source == null || trigger == null
                || !CoreConfig.masteryEnabled()) {
            return;
        }
        MasterySpec spec = MasteryDeclarations.get(source).orElse(null);
        if (spec == null) {
            return; // opted out: the system is invisible for this ability
        }
        if (!spec.triggers().contains(trigger)) {
            return; // declared intent: only the named triggers grant
        }
        double amount = Double.isNaN(rawAmount)
                ? resolveGain(spec, player, source, trigger)
                : rawAmount;
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        amount *= CoreConfig.masteryGlobalGainMultiplier();
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        var data = MasteryAttachments.data(player);
        for (Map.Entry<MasteryTarget, Double> entry
                : MasteryRouting.split(source, spec.shares(), amount).entrySet()) {
            data.addXp(entry.getKey().key(), entry.getValue());
        }
    }

    /**
     * The gain for one use: the named handler when the spec has one,
     * otherwise the base gain. An unknown or misbehaving handler logs an
     * error and falls back to the base gain - a gain read never fails an
     * activation.
     */
    static double resolveGain(MasterySpec spec, @Nullable ServerPlayer player,
            ResourceLocation source, MasteryTrigger trigger) {
        if (spec.gainHandler() == null) {
            return spec.baseGain();
        }
        MasteryGainHandlers.GainHandler handler =
                MasteryGainHandlers.get(spec.gainHandler()).orElse(null);
        if (handler == null) {
            LOGGER.error("Unknown mastery gain handler '{}' for {} - using the base gain {}",
                    spec.gainHandler(), source, spec.baseGain());
            return spec.baseGain();
        }
        try {
            double gain = handler.gain(player, source, trigger);
            if (!Double.isFinite(gain) || gain < 0.0) {
                LOGGER.error("Mastery gain handler '{}' for {} returned {} - using the base gain {}",
                        spec.gainHandler(), source, gain, spec.baseGain());
                return spec.baseGain();
            }
            return gain;
        } catch (RuntimeException e) {
            LOGGER.error("Mastery gain handler '{}' for {} threw - using the base gain {}",
                    spec.gainHandler(), source, spec.baseGain(), e);
            return spec.baseGain();
        }
    }
}
