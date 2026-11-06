package org.bitwisemadness.abilitycore;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.RefundableConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.slf4j.Logger;

/**
 * Core's built-in resource pairs (REQ-CORE-04, issue #88; five since the
 * deliberate {@code free} pair, #93): health,
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

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String HEALTH = "ability_core:health";
    public static final String HUNGER = "ability_core:hunger";
    public static final String EXPERIENCE_POINTS = "ability_core:experience_points";
    public static final String EXPERIENCE_LEVELS = "ability_core:experience_levels";
    /** The deliberate zero-cost resource (issue #93) - see {@link #registerAll}. */
    public static final String FREE = "ability_core:free";

    private BuiltinResources() {
    }

    /**
     * Declares all five built-in pairs. Call once, at mod construction.
     *
     * <p>{@code free} (issue #93) is the deliberate zero-cost route: its
     * provider always answers SUFFICIENT with <b>amount 0</b> (the check's
     * amount parameter is ignored - the truth is always "nothing"), and its
     * consumer is a no-op with a debug line. An ability uses it <b>only by
     * naming it</b> as its cost - {@code cost == null} + EXTERNAL still
     * refuses undeclared, so there is no implicit free anywhere.</p>
     */
    public static void registerAll() {
        ResourceProviders.declare(HEALTH, (abilityId, player, amount) ->
                poolCheck(HEALTH, player.getHealth(), amount));
        ResourceConsumers.declare(HEALTH, new RefundableConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.setHealth(Math.max(0.0f, player.getHealth() - (float) snapshot.amount()));
            }

            @Override
            public void refund(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.setHealth((float) refundContinuous(
                        player.getHealth(), snapshot.amount(), player.getMaxHealth()));
            }
        });

        ResourceProviders.declare(HUNGER, (abilityId, player, amount) ->
                poolCheck(HUNGER, player.getFoodData().getFoodLevel(), amount));
        ResourceConsumers.declare(HUNGER, new RefundableConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.getFoodData().setFoodLevel(
                        deductIntegral(player.getFoodData().getFoodLevel(), snapshot.amount()));
            }

            @Override
            public void refund(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.getFoodData().setFoodLevel(refundIntegral(
                        player.getFoodData().getFoodLevel(), snapshot.amount(), 20));
            }
        });

        ResourceProviders.declare(EXPERIENCE_POINTS, (abilityId, player, amount) ->
                poolCheck(EXPERIENCE_POINTS, player.totalExperience, amount));
        ResourceConsumers.declare(EXPERIENCE_POINTS, new RefundableConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.giveExperiencePoints(-deductedIntegral(player.totalExperience, snapshot.amount()));
            }

            @Override
            public void refund(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.giveExperiencePoints(refundIntegral(
                        player.totalExperience, snapshot.amount(), Integer.MAX_VALUE)
                        - player.totalExperience);
            }
        });

        ResourceProviders.declare(EXPERIENCE_LEVELS, (abilityId, player, amount) ->
                poolCheck(EXPERIENCE_LEVELS, player.experienceLevel, amount));
        ResourceConsumers.declare(EXPERIENCE_LEVELS, new RefundableConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.giveExperienceLevels(-deductedIntegral(player.experienceLevel, snapshot.amount()));
            }

            @Override
            public void refund(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                player.giveExperienceLevels(refundIntegral(
                        player.experienceLevel, snapshot.amount(), Integer.MAX_VALUE)
                        - player.experienceLevel);
            }
        });

        // issue #93: the deliberate zero-cost pair - always sufficient at 0,
        // charge and refund are verified no-ops (debug lines only)
        ResourceProviders.declare(FREE, (abilityId, player, amount) ->
                Affordability.sufficient(FREE, 0.0));
        ResourceConsumers.declare(FREE, new RefundableConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                LOGGER.debug("Free resource '{}' charged nothing for {}", abilityId, snapshot.amount());
            }

            @Override
            public void refund(net.minecraft.resources.ResourceLocation abilityId,
                    ServerPlayer player, Affordability snapshot) {
                LOGGER.debug("Free resource '{}' refunded nothing for {}", abilityId, snapshot.amount());
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
     * The post-deduction <b>level</b> for integral pools (food's
     * {@code setFoodLevel}): the amount rounds <b>up</b> so a fractional cost
     * never under-charges what the check approved, clamped at zero so a pool
     * never goes negative. For a <i>taken-amount</i> delta - what a
     * {@code giveExperience*} call needs - use {@link #deductedIntegral}.
     */
    static int deductIntegral(double balance, double amount) {
        return Math.max(0, (int) balance - (int) Math.ceil(amount));
    }

    /**
     * The amount <b>actually taken</b> from an integral pool (XP's
     * {@code giveExperience*}): {@code ceil(amount)} clamped to the balance.
     * {@code balance - deductedIntegral(...) == deductIntegral(...)} holds
     * for any non-negative balance - the two helpers are the subtraction
     * split into its two halves (issue #108).
     */
    static int deductedIntegral(double balance, double amount) {
        return (int) Math.max(0L, Math.min((long) balance, (long) Math.ceil(amount)));
    }

    /**
     * The refund target for an integral pool (food, XP): {@code ceil(amount)}
     * added back - the mirror of {@link #deductedIntegral}'s rounding - capped
     * at {@code max} (food's 20; the XP pools pass {@code Integer.MAX_VALUE}).
     */
    static int refundIntegral(double balance, double amount, int max) {
        return (int) Math.min(max, balance + Math.ceil(amount));
    }

    /**
     * The refund target for a continuous pool (health): the amount added
     * back, capped at the pool's maximum so a refund never overheals.
     */
    static double refundContinuous(double balance, double amount, double max) {
        return Math.min(max, balance + amount);
    }
}
