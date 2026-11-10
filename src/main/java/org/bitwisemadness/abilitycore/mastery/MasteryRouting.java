package org.bitwisemadness.abilitycore.mastery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;

/**
 * The pure share-split of one mastery grant (REQ-CORE-12): each declared
 * share takes its fraction, the self remainder (source ability, capped at
 * 0) keeps the rest, and a share list summing to exactly 1 leaves self
 * with nothing - "any combination" includes routing everything away.
 *
 * <p>Package-private on purpose: routing is the part worth pinning
 * without a player, a config or an attachment.</p>
 */
public final class MasteryRouting {

    private MasteryRouting() {
    }

    /**
     * Splits {@code amount} across the share list plus the self remainder.
     *
     * @param source the ability being used (its own target is the remainder)
     * @param shares the spec's external shares (validated to sum {@code <= 1})
     * @param amount the validated grant (finite, positive)
     * @return target -> XP in declaration order, self last; never empty for
     *         a positive amount (self always gets a key, even at 0 - callers
     *         skip non-positive entries)
     */
    public static Map<MasteryTarget, Double> split(ResourceLocation source,
            List<MasterySpec.Share> shares, double amount) {
        Map<MasteryTarget, Double> out = new LinkedHashMap<>();
        double remainder = amount;
        for (MasterySpec.Share share : shares) {
            double part = amount * share.share();
            remainder -= part;
            if (part > 0.0 && Double.isFinite(part)) {
                out.merge(share.target(), part, Double::sum);
            }
        }
        if (remainder > 1.0e-9) {
            out.put(MasteryTarget.ability(source), remainder);
        }
        return out;
    }
}
