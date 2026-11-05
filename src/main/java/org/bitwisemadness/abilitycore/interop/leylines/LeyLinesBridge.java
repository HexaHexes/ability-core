package org.bitwisemadness.abilitycore.interop.leylines;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.slf4j.Logger;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.PowerRequest;

/**
 * The upstream-typed half of the Ley Lines bridge (issue #96): {@code quoteAt}
 * at the player's position is the affordability check, {@code spendAt}
 * post-ACTED is the charge. Package-private with package-private members, so
 * no public signature anywhere in core names an upstream type (the signature
 * gate watches the interop package; the leak gate watches the contract ones).
 *
 * <p>Loaded only when {@code LeyLinesResources}' gate saw the mod present -
 * an absent mod never links this class.</p>
 *
 * <p>Fail-closed mapping of their contract: outside a usable nexus field
 * ({@code quoteAt} empty) or a fractional amount (their balances are whole
 * units) reads as INSUFFICIENT - refused before activation; a failed spend
 * leaves balances untouched (their guarantee) and is logged, never re-run
 * (core's post-activation posture).</p>
 */
final class LeyLinesBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LeyLinesBridge() {
    }

    static boolean declarePair(ResourceLocation currencyId) {
        String resourceId = currencyId.toString();
        ResourceProviders.declare(resourceId, (abilityId, player, amount) -> {
            if (player == null) {
                // no level to quote (unit fixtures): not affordable, fail closed
                return Affordability.unknown(resourceId);
            }
            if (amount > 0 && amount != Math.rint(amount)) {
                return Affordability.insufficient(resourceId, amount);
            }
            var quote = LeyLinesApi.forLevel(player.serverLevel()).power()
                    .quoteAt(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            if (quote.isEmpty()) {
                return Affordability.insufficient(resourceId, amount);
            }
            long balance = quote.get().account().currency(currencyId);
            return balance >= (long) amount
                    ? Affordability.sufficient(resourceId, amount)
                    : Affordability.insufficient(resourceId, amount);
        });
        ResourceConsumers.declare(resourceId, new ResourceConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player, Affordability snapshot) {
                long units = (long) snapshot.amount();
                if (units <= 0) {
                    LOGGER.debug("Ley Lines currency '{}' charged nothing for {}", resourceId, abilityId);
                    return;
                }
                var result = LeyLinesApi.forLevel(player.serverLevel()).power().spendAt(
                        player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                        currencyId, units, new PowerRequest.Consumer(abilityId));
                if (!result.succeeded()) {
                    // post-activation: cannot un-act; their contract leaves balances
                    // untouched on failure, so log loudly and report success truthfully
                    LOGGER.error("Ley Lines spend of {} {} for {} failed: {}",
                            units, resourceId, abilityId, result.status());
                }
            }
        });
        return true;
    }
}
