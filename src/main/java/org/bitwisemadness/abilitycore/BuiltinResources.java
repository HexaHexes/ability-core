package org.bitwisemadness.abilitycore;

import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceProviders;

/**
 * Core's four built-in resource pairs (REQ-CORE-04, issue #88): health,
 * hunger, experience points and experience levels - the vanilla pools every
 * pack can price abilities against without an addon. Registered once at mod
 * construction ({@link AbilityCore}'s constructor), which the pairing rule
 * and the registration timing contract both assume.
 *
 * <p>Each pair is a thin player adapter over <b>pure helpers</b>
 * ({@link #poolCheck}, {@link #deductIntegral}) so the arithmetic and the
 * affordability verdict are unit-testable without a ServerPlayer fixture -
 * core has none. Providers are read-only; consumers deduct after a
 * successful activation (EXTERNAL chargingModel only, ADR-002).</p>
 */
public final class BuiltinResources {

    public static final String HEALTH = "ability_core:health";
    public static final String HUNGER = "ability_core:hunger";
    public static final String EXPERIENCE_POINTS = "ability_core:experience_points";
    public static final String EXPERIENCE_LEVELS = "ability_core:experience_levels";

    private BuiltinResources() {
    }

    /** Declares all four provider/consumer pairs. Call once, at mod construction. */
    public static void registerAll() {
        ResourceProviders.declare(HEALTH, (abilityId, player, amount) ->
                poolCheck(HEALTH, player.getHealth(), amount));
        ResourceConsumers.declare(HEALTH, new ResourceConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.setHealth(Math.max(0.0f, player.getHealth() - (float) snapshot.amount()));
            }
        });

        ResourceProviders.declare(HUNGER, (abilityId, player, amount) ->
                poolCheck(HUNGER, player.getFoodData().getFoodLevel(), amount));
        ResourceConsumers.declare(HUNGER, new ResourceConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.getFoodData().setFoodLevel(
                        deductIntegral(player.getFoodData().getFoodLevel(), snapshot.amount()));
            }
        });

        ResourceProviders.declare(EXPERIENCE_POINTS, (abilityId, player, amount) ->
                poolCheck(EXPERIENCE_POINTS, player.totalExperience, amount));
        ResourceConsumers.declare(EXPERIENCE_POINTS, new ResourceConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.giveExperiencePoints(-deductIntegral(player.totalExperience, snapshot.amount()));
            }
        });

        ResourceProviders.declare(EXPERIENCE_LEVELS, (abilityId, player, amount) ->
                poolCheck(EXPERIENCE_LEVELS, player.experienceLevel, amount));
        ResourceConsumers.declare(EXPERIENCE_LEVELS, new ResourceConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.giveExperienceLevels(-deductIntegral(player.experienceLevel, snapshot.amount()));
            }
        });
    }

    /**
     * The pure pool verdict: enough of the balance covers the amount (equal
     * counts - a balance exactly equal to the cost is affordable).
     */
    static Affordability poolCheck(String resourceId, double balance, double amount) {
        return balance >= amount
                ? Affordability.sufficient(resourceId, amount)
                : Affordability.insufficient(resourceId, amount);
    }

    /**
     * The pure deduction for integral pools (food, XP): the amount rounds
     * <b>up</b> so a fractional cost never under-charges what the check
     * approved, clamped at zero so a pool never goes negative.
     */
    static int deductIntegral(double balance, double amount) {
        return Math.max(0, (int) balance - (int) Math.ceil(amount));
    }
}
